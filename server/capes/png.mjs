/**
 * PNG lesen und neu schreiben, ohne Abhängigkeiten.
 *
 * Hochgeladene Capes werden vollständig dekodiert und als schlichtes RGBA-PNG neu
 * kodiert. So kommt nichts Fremdes durch (Metadaten, Textblöcke, kaputte Chunks), und
 * die Cape-ID hängt nur an den Pixeln: dasselbe Bild hat immer dieselbe ID, egal mit
 * welchem Programm es gespeichert wurde. Der Launcher rechnet die ID genauso aus
 * (`share_id` in `launcher/src-tauri/src/integrations/salami_capes.rs`).
 *
 * Unterstützt: 8 Bit pro Kanal, ohne Interlacing, Graustufen/RGB/Palette, mit oder ohne Alpha.
 */
import { createHash } from "node:crypto";
import zlib from "node:zlib";

const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const MAX_CHUNKS = 10_000;
/** Kanäle je Farbtyp (nur 8 Bit). */
const CHANNELS = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 };

export class PngError extends Error {}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(...parts) {
  let crc = 0xffffffff;
  for (const part of parts) {
    for (let i = 0; i < part.length; i++) crc = CRC_TABLE[(crc ^ part[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

/** Cape-Layout wie im Launcher und im Mod: Breite = 2 × Höhe, 64–2048 px breit, Vielfaches von 64. */
export function isCapeSize(width, height) {
  return width === height * 2 && width >= 64 && width <= 2048 && width % 64 === 0;
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  return pb <= pc ? b : c;
}

/**
 * Dekodiert ein PNG im Cape-Format zu RGBA.
 * @returns {{ width: number, height: number, rgba: Buffer }}
 */
export function decodeCape(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < SIGNATURE.length + 12 || !buf.subarray(0, 8).equals(SIGNATURE)) {
    throw new PngError("keine PNG-Datei");
  }
  let pos = 8;
  let header = null;
  let palette = null;
  let transparency = null;
  const idat = [];
  let ended = false;
  let chunks = 0;

  while (!ended) {
    if (++chunks > MAX_CHUNKS || pos + 12 > buf.length) throw new PngError("PNG ist unvollständig");
    const length = buf.readUInt32BE(pos);
    const type = buf.subarray(pos + 4, pos + 8);
    const dataEnd = pos + 8 + length;
    if (length > 0x7fffffff || dataEnd + 4 > buf.length) throw new PngError("PNG ist unvollständig");
    const data = buf.subarray(pos + 8, dataEnd);
    if (crc32(type, data) !== buf.readUInt32BE(dataEnd)) throw new PngError("PNG ist beschädigt (Prüfsumme)");
    const name = type.toString("latin1");
    pos = dataEnd + 4;

    if (!header && name !== "IHDR") throw new PngError("PNG ist beschädigt (IHDR fehlt)");
    switch (name) {
      case "IHDR": {
        if (header || length !== 13) throw new PngError("PNG ist beschädigt (IHDR)");
        header = {
          width: data.readUInt32BE(0),
          height: data.readUInt32BE(4),
          bitDepth: data[8],
          colorType: data[9],
          compression: data[10],
          filter: data[11],
          interlace: data[12],
        };
        if (!isCapeSize(header.width, header.height)) {
          throw new PngError(
            `kein Cape-Format: ${header.width}x${header.height} (erlaubt: 64x32, 128x64 … 2048x1024)`,
          );
        }
        if (header.bitDepth !== 8 || !(header.colorType in CHANNELS)) {
          throw new PngError("nicht unterstütztes PNG – bitte als 8-Bit-PNG (RGBA) speichern");
        }
        if (header.compression !== 0 || header.filter !== 0) throw new PngError("PNG ist beschädigt (IHDR)");
        if (header.interlace !== 0) throw new PngError("PNG mit Interlacing wird nicht unterstützt");
        break;
      }
      case "PLTE":
        if (length === 0 || length % 3 !== 0 || length > 768) throw new PngError("PNG ist beschädigt (Palette)");
        palette = data;
        break;
      case "tRNS":
        transparency = data;
        break;
      case "IDAT":
        idat.push(data);
        break;
      case "IEND":
        ended = true;
        break;
      default:
        // Kritische unbekannte Chunks (Großbuchstabe vorne) dürfen nicht ignoriert werden.
        if ((type[0] & 0x20) === 0) throw new PngError(`PNG enthält unbekannten Chunk ${name}`);
    }
  }
  if (!header || idat.length === 0) throw new PngError("PNG enthält keine Bilddaten");
  if (header.colorType === 3 && !palette) throw new PngError("PNG ist beschädigt (Palette fehlt)");

  const { width, height, colorType } = header;
  const channels = CHANNELS[colorType];
  const stride = width * channels;
  const expected = height * (stride + 1);
  let raw;
  try {
    raw = zlib.inflateSync(Buffer.concat(idat), { maxOutputLength: expected });
  } catch {
    throw new PngError("PNG-Bilddaten sind beschädigt");
  }
  if (raw.length !== expected) throw new PngError("PNG-Bilddaten haben die falsche Länge");

  // Filter rückgängig machen, Zeile für Zeile
  const pixels = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const src = y * (stride + 1) + 1;
    const row = y * stride;
    const prev = row - stride;
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? pixels[row + x - channels] : 0;
      const b = y > 0 ? pixels[prev + x] : 0;
      const c = x >= channels && y > 0 ? pixels[prev + x - channels] : 0;
      let value = raw[src + x];
      switch (filter) {
        case 0:
          break;
        case 1:
          value += a;
          break;
        case 2:
          value += b;
          break;
        case 3:
          value += (a + b) >> 1;
          break;
        case 4:
          value += paeth(a, b, c);
          break;
        default:
          throw new PngError("PNG-Bilddaten sind beschädigt (Filter)");
      }
      pixels[row + x] = value & 0xff;
    }
  }

  const rgba = Buffer.alloc(width * height * 4);
  const count = width * height;
  for (let i = 0; i < count; i++) {
    const o = i * 4;
    switch (colorType) {
      case 6:
        pixels.copy(rgba, o, i * 4, i * 4 + 4);
        break;
      case 2: {
        const r = pixels[i * 3];
        const g = pixels[i * 3 + 1];
        const b = pixels[i * 3 + 2];
        rgba[o] = r;
        rgba[o + 1] = g;
        rgba[o + 2] = b;
        const keyed =
          transparency?.length === 6 &&
          transparency.readUInt16BE(0) === r &&
          transparency.readUInt16BE(2) === g &&
          transparency.readUInt16BE(4) === b;
        rgba[o + 3] = keyed ? 0 : 255;
        break;
      }
      case 3: {
        const index = pixels[i];
        if (index * 3 + 2 >= palette.length) throw new PngError("PNG ist beschädigt (Palettenindex)");
        rgba[o] = palette[index * 3];
        rgba[o + 1] = palette[index * 3 + 1];
        rgba[o + 2] = palette[index * 3 + 2];
        rgba[o + 3] = transparency && index < transparency.length ? transparency[index] : 255;
        break;
      }
      case 0: {
        const v = pixels[i];
        rgba[o] = rgba[o + 1] = rgba[o + 2] = v;
        rgba[o + 3] = transparency?.length === 2 && transparency.readUInt16BE(0) === v ? 0 : 255;
        break;
      }
      case 4: {
        const v = pixels[i * 2];
        rgba[o] = rgba[o + 1] = rgba[o + 2] = v;
        rgba[o + 3] = pixels[i * 2 + 1];
        break;
      }
    }
  }
  return { width, height, rgba };
}

function chunk(name, data) {
  const type = Buffer.from(name, "latin1");
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  type.copy(out, 4);
  data.copy(out, 8);
  out.writeUInt32BE(crc32(type, data), 8 + data.length);
  return out;
}

/** Schreibt RGBA als schlichtes PNG (nur IHDR, IDAT, IEND). */
export function encodeRgba(width, height, rgba) {
  const stride = width * 4;
  const raw = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // Bittiefe
  ihdr[9] = 6; // RGBA
  return Buffer.concat([
    SIGNATURE,
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/** Cape-ID: SHA-256 über Breite, Höhe (je 4 Byte Big Endian) und die RGBA-Pixel, hex. */
export function capeId(width, height, rgba) {
  const size = Buffer.alloc(8);
  size.writeUInt32BE(width, 0);
  size.writeUInt32BE(height, 4);
  return createHash("sha256").update(size).update(rgba).digest("hex");
}
