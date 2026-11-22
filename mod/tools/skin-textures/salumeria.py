"""Schriften, Schrift-Definitionen und Sprites des Salumeria-Looks (salami-core).

    python -I salumeria.py <mod/src/main/resources/assets/salami>

1. Schrift: Die Launcher-Schriften (launcher/public/fonts/cormorant-garamond*.ttf) sind variable
   Schriften mit der Achse „wght“ (300–700) und dem dünnen Light (300) als Standard. Minecraft lädt
   TTF-Schriften über FreeType immer im Standard und kann keine Achse setzen – Light ist bei GUI-Größe 2
   zu dünn zum Lesen. Daraus wird ein fester Schnitt Medium (500) gerechnet: Glyphen-Varianten (gvar)
   samt avar anwenden, Vorschub aus den Phantompunkten, Variations- und Layout-Tabellen weglassen
   (Minecraft nutzt weder Kerning noch Ligaturen). Ohne fontTools, nur Standardbibliothek; das Ergebnis
   ist pixelgleich mit der variablen Schrift bei wght 500 (mit Pillow geprüft).
   Die Lizenz (SIL OFL 1.1, ohne reservierten Namen) erlaubt veränderte Fassungen; sie liegt als
   font/cormorant-garamond-OFL.txt neben den Schriften.

2. Schrift-Definitionen font/salumeria/<schnitt>_<oversample×10>.json: Minecraft zeichnet TTF-Glyphen
   ohne Glättung (nächster Bildpunkt). Scharf ist die Schrift nur, wenn „oversample“ genau so viele
   Bildschirmpixel hat wie ein GUI-Pixel – deshalb gibt es jeden Schnitt in mehreren Auflösungen, und
   der Look wählt beim Zeichnen die passende (gui/skin/salumeria/Serif.java).

3. Sprites textures/gui/sprites/salumeria/ (doppelte Auflösung wie bei der Theke): das Logo.
"""
import json
import shutil
import struct
import sys
from pathlib import Path

DROP = {b"fvar", b"gvar", b"avar", b"HVAR", b"MVAR", b"STAT", b"cvar", b"GDEF", b"GPOS", b"GSUB", b"DSIG"}


def f2dot14(v):
    return (v if v < 0x8000 else v - 0x10000) / 16384.0


def read_tables(data):
    num = struct.unpack(">H", data[4:6])[0]
    tables = {}
    for i in range(num):
        tag, _, off, length = struct.unpack(">4sIII", data[12 + 16 * i:28 + 16 * i])
        tables[tag] = data[off:off + length]
    return tables


# ------------------------------------------------------------------ Achse normalisieren


def normalized(tables, weight):
    fvar = tables[b"fvar"]
    axes_off, _, axis_count, axis_size = struct.unpack(">HHHH", fvar[4:12])
    coords = []
    for a in range(axis_count):
        o = axes_off + a * axis_size
        tag, mn, df, mx = struct.unpack(">4siii", fvar[o:o + 16])
        mn, df, mx = mn / 65536, df / 65536, mx / 65536
        v = max(mn, min(mx, float(weight) if tag == b"wght" else df))
        if v < df:
            n = -(df - v) / (df - mn)
        elif v > df:
            n = (v - df) / (mx - df)
        else:
            n = 0.0
        coords.append(n)
    if b"avar" in tables:
        avar = tables[b"avar"]
        o = 8
        for a in range(axis_count):
            count = struct.unpack(">H", avar[o:o + 2])[0]
            o += 2
            pairs = []
            for _ in range(count):
                fr, to = struct.unpack(">HH", avar[o:o + 4])
                pairs.append((f2dot14(fr), f2dot14(to)))
                o += 4
            n = coords[a]
            for k in range(1, len(pairs)):
                (x0, y0), (x1, y1) = pairs[k - 1], pairs[k]
                if x0 <= n <= x1:
                    coords[a] = y0 if x1 == x0 else y0 + (n - x0) * (y1 - y0) / (x1 - x0)
                    break
    # wie FreeType/fontTools auf F2Dot14 runden
    return [round(c * 16384) / 16384 for c in coords]


def scalar(coords, peak, start=None, end=None):
    s = 1.0
    for i, c in enumerate(coords):
        p = peak[i]
        if p == 0:
            continue
        if start is not None:
            a, b = start[i], end[i]
            if a > p or p > b or (a < 0 < b):
                continue
            if c < a or c > b:
                return 0.0
            if c == p:
                continue
            s *= (c - a) / (p - a) if c < p else (b - c) / (b - p)
        else:
            if c == 0 or (c < 0) != (p < 0) or abs(c) > abs(p):
                return 0.0
            s *= c / p
    return s


# ------------------------------------------------------------------ glyf lesen/schreiben


def parse_glyph(raw):
    if not raw:
        return None
    n = struct.unpack(">h", raw[0:2])[0]
    if n >= 0:
        ends = list(struct.unpack(">%dH" % n, raw[10:10 + 2 * n]))
        o = 10 + 2 * n
        ilen = struct.unpack(">H", raw[o:o + 2])[0]
        o += 2 + ilen
        count = ends[-1] + 1 if ends else 0
        flags = []
        while len(flags) < count:
            f = raw[o]
            o += 1
            flags.append(f)
            if f & 8:
                r = raw[o]
                o += 1
                flags.extend([f] * r)
        xs, ys = [], []
        for coords, short, same in ((xs, 2, 16), (ys, 4, 32)):
            v = 0
            for f in flags:
                if f & short:
                    d = raw[o]
                    o += 1
                    v += d if f & same else -d
                elif not f & same:
                    v += struct.unpack(">h", raw[o:o + 2])[0]
                    o += 2
                coords.append(v)
        return {"simple": True, "ends": ends, "on": [f & 1 for f in flags], "overlap": bool(flags and flags[0] & 0x40),
                "x": xs, "y": ys}
    comps = []
    o = 10
    while True:
        flags, gid = struct.unpack(">HH", raw[o:o + 4])
        o += 4
        if flags & 1:
            a1, a2 = struct.unpack(">hh" if flags & 2 else ">HH", raw[o:o + 4])
            o += 4
        else:
            a1, a2 = struct.unpack(">bb" if flags & 2 else ">BB", raw[o:o + 2])
            o += 2
        if flags & 8:
            t = raw[o:o + 2]
            o += 2
        elif flags & 0x40:
            t = raw[o:o + 4]
            o += 4
        elif flags & 0x80:
            t = raw[o:o + 8]
            o += 8
        else:
            t = b""
        comps.append({"flags": flags, "gid": gid, "dx": a1, "dy": a2, "t": t})
        if not flags & 0x20:
            break
    return {"simple": False, "comps": comps}


def transform(comp):
    f, t = comp["flags"], comp["t"]
    if f & 8:
        s = f2dot14(struct.unpack(">H", t)[0])
        return s, 0.0, 0.0, s
    if f & 0x40:
        a, d = struct.unpack(">HH", t)
        return f2dot14(a), 0.0, 0.0, f2dot14(d)
    if f & 0x80:
        a, b, c, d = struct.unpack(">HHHH", t)
        return f2dot14(a), f2dot14(b), f2dot14(c), f2dot14(d)
    return 1.0, 0.0, 0.0, 1.0


def write_glyph(g):
    if g is None:
        return b""
    if g["simple"]:
        xs, ys = g["x"], g["y"]
        out = bytearray(struct.pack(">hhhhh", len(g["ends"]), *g["bbox"]))
        out += struct.pack(">%dH" % len(g["ends"]), *g["ends"])
        out += struct.pack(">H", 0)
        # kurze Vektoren (1 Byte) wo möglich, unveränderte Koordinaten ganz ohne Byte
        flags, xb, yb = [], bytearray(), bytearray()
        px = py = 0
        for i, on in enumerate(g["on"]):
            f = (1 if on else 0) | (0x40 if i == 0 and g["overlap"] else 0)
            for v, prev, buf, short, same in ((xs[i], px, xb, 2, 16), (ys[i], py, yb, 4, 32)):
                d = v - prev
                if d == 0:
                    f |= same
                elif -255 <= d <= 255:
                    f |= short | (same if d > 0 else 0)
                    buf.append(abs(d))
                else:
                    buf += struct.pack(">h", d)
            px, py = xs[i], ys[i]
            flags.append(f)
        k = 0
        while k < len(flags):
            r = 0
            while k + r + 1 < len(flags) and flags[k + r + 1] == flags[k] and r < 255:
                r += 1
            if r:
                out += bytes([flags[k] | 8, r])
            else:
                out.append(flags[k])
            k += r + 1
        out += xb + yb
        return bytes(out)
    out = bytearray(struct.pack(">hhhhh", -1, *g["bbox"]))
    for i, c in enumerate(g["comps"]):
        flags = (c["flags"] | 1) & ~0x100  # Wörter für die Versätze, keine Anweisungen
        if i < len(g["comps"]) - 1:
            flags |= 0x20
        else:
            flags &= ~0x20
        out += struct.pack(">HH", flags, c["gid"])
        out += struct.pack(">hh" if flags & 2 else ">HH", c["dx"], c["dy"])
        out += c["t"]
    return bytes(out)


# ------------------------------------------------------------------ gvar


def packed_points(data, o):
    n = data[o]
    o += 1
    if n == 0:
        return None, o
    if n & 0x80:
        n = ((n & 0x7F) << 8) | data[o]
        o += 1
    pts = []
    last = 0
    while len(pts) < n:
        ctl = data[o]
        o += 1
        run = (ctl & 0x7F) + 1
        for _ in range(run):
            if ctl & 0x80:
                d = struct.unpack(">H", data[o:o + 2])[0]
                o += 2
            else:
                d = data[o]
                o += 1
            last += d
            pts.append(last)
    return pts, o


def packed_deltas(data, o, n):
    out = []
    while len(out) < n:
        ctl = data[o]
        o += 1
        run = (ctl & 0x3F) + 1
        if ctl & 0x80 and ctl & 0x40:
            out.extend(struct.unpack(">%di" % run, data[o:o + 4 * run]))
            o += 4 * run
        elif ctl & 0x80:
            out.extend([0] * run)
        elif ctl & 0x40:
            out.extend(struct.unpack(">%dh" % run, data[o:o + 2 * run]))
            o += 2 * run
        else:
            out.extend(struct.unpack(">%db" % run, data[o:o + run]))
            o += run
    return out, o


def iup(deltas, coords, ends):
    """Fehlende Deltas eines einfachen Glyphs zwischen gesetzten Punkten einer Kontur ableiten."""
    start = 0
    for end in ends:
        idx = range(start, end + 1)
        touched = [i for i in idx if deltas[i] is not None]
        if not touched:
            for i in idx:
                deltas[i] = (0.0, 0.0)
        elif len(touched) == 1:
            for i in idx:
                deltas[i] = deltas[touched[0]]
        else:
            for k, t in enumerate(touched):
                nxt = touched[(k + 1) % len(touched)]
                i = t + 1
                while True:
                    if i > end:
                        i = start
                    if i == nxt:
                        break
                    d = []
                    for ax in (0, 1):
                        c1, c2 = coords[t][ax], coords[nxt][ax]
                        d1, d2 = deltas[t][ax], deltas[nxt][ax]
                        c = coords[i][ax]
                        if c1 == c2:
                            d.append(d1 if d1 == d2 else 0.0)
                        elif c <= min(c1, c2):
                            d.append(d1 if c1 < c2 else d2)
                        elif c >= max(c1, c2):
                            d.append(d1 if c1 > c2 else d2)
                        else:
                            d.append(d1 + (c - c1) * (d2 - d1) / (c2 - c1))
                    deltas[i] = tuple(d)
                    i += 1
        start = end + 1
    return deltas


def glyph_deltas(gvar, gid, coords, axis_count, shared, npoints, base, ends):
    flags = struct.unpack(">H", gvar[14:16])[0]
    data_off = struct.unpack(">I", gvar[16:20])[0]
    if flags & 1:
        a, b = struct.unpack(">II", gvar[20 + 4 * gid:28 + 4 * gid])
    else:
        a, b = struct.unpack(">HH", gvar[20 + 2 * gid:24 + 2 * gid])
        a, b = a * 2, b * 2
    total = [[0.0, 0.0] for _ in range(npoints)]
    if a == b:
        return total
    v = gvar[data_off + a:data_off + b]
    count, off = struct.unpack(">HH", v[0:4])
    shared_points = None
    o = 4
    headers = []
    for _ in range(count & 0x0FFF):
        size, index = struct.unpack(">HH", v[o:o + 4])
        o += 4
        if index & 0x8000:
            peak = [f2dot14(x) for x in struct.unpack(">%dH" % axis_count, v[o:o + 2 * axis_count])]
            o += 2 * axis_count
        else:
            peak = shared[index & 0x0FFF]
        start = end = None
        if index & 0x4000:
            start = [f2dot14(x) for x in struct.unpack(">%dH" % axis_count, v[o:o + 2 * axis_count])]
            o += 2 * axis_count
            end = [f2dot14(x) for x in struct.unpack(">%dH" % axis_count, v[o:o + 2 * axis_count])]
            o += 2 * axis_count
        headers.append((size, index, peak, start, end))
    d = off
    if count & 0x8000:
        shared_points, d = packed_points(v, d)
    for size, index, peak, start, end in headers:
        next_d = d + size
        s = scalar(coords, peak, start, end)
        if s == 0:
            d = next_d
            continue
        if index & 0x2000:
            pts, d = packed_points(v, d)
        else:
            pts = shared_points
        n = npoints if pts is None else len(pts)
        xs, d = packed_deltas(v, d, n)
        ys, d = packed_deltas(v, d, n)
        if pts is None:
            for i in range(npoints):
                total[i][0] += xs[i] * s
                total[i][1] += ys[i] * s
        else:
            part = [None] * npoints
            for k, p in enumerate(pts):
                if p < npoints:
                    part[p] = (xs[k], ys[k])
            if ends is not None:
                # nur echte Punkte ableiten, Phantompunkte bleiben ohne Delta
                outline = iup(part[:npoints - 4], base, ends)
                part = outline + part[npoints - 4:]
            for i in range(npoints):
                if part[i] is not None:
                    total[i][0] += part[i][0] * s
                    total[i][1] += part[i][1] * s
        d = next_d
    return total


# ------------------------------------------------------------------ Hauptteil


def instance(src, dst, weight):
    data = open(src, "rb").read()
    tables = read_tables(data)
    coords = normalized(tables, weight)
    num_glyphs = struct.unpack(">H", tables[b"maxp"][4:6])[0]
    head = bytearray(tables[b"head"])
    long_loca = struct.unpack(">h", head[50:52])[0] == 1
    loca_raw = tables[b"loca"]
    if long_loca:
        loca = struct.unpack(">%dI" % (num_glyphs + 1), loca_raw[:4 * (num_glyphs + 1)])
    else:
        loca = [x * 2 for x in struct.unpack(">%dH" % (num_glyphs + 1), loca_raw[:2 * (num_glyphs + 1)])]
    glyf = tables[b"glyf"]
    hhea = bytearray(tables[b"hhea"])
    n_hm = struct.unpack(">H", hhea[34:36])[0]
    hmtx_raw = tables[b"hmtx"]
    adv, lsb = [], []
    for i in range(num_glyphs):
        if i < n_hm:
            a, l = struct.unpack(">Hh", hmtx_raw[4 * i:4 * i + 4])
        else:
            a = adv[-1]
            l = struct.unpack(">h", hmtx_raw[4 * n_hm + 2 * (i - n_hm):4 * n_hm + 2 * (i - n_hm) + 2])[0]
        adv.append(a)
        lsb.append(l)

    gvar = tables[b"gvar"]
    axis_count, shared_count, shared_off = struct.unpack(">HHI", gvar[4:12])
    shared = []
    for i in range(shared_count):
        o = shared_off + i * 2 * axis_count
        shared.append([f2dot14(x) for x in struct.unpack(">%dH" % axis_count, gvar[o:o + 2 * axis_count])])

    glyphs = [parse_glyph(glyf[loca[i]:loca[i + 1]]) for i in range(num_glyphs)]
    new_adv = list(adv)
    for gid, g in enumerate(glyphs):
        if g is None:
            npts, base, ends = 4, [], None
        elif g["simple"]:
            npts = len(g["x"]) + 4
            base = list(zip(g["x"], g["y"]))
            ends = g["ends"]
        else:
            npts = len(g["comps"]) + 4
            base, ends = None, None
        deltas = glyph_deltas(gvar, gid, coords, axis_count, shared, npts, base, ends)
        # Phantompunkte: links (xMin - lsb), rechts (+ Vorschub)
        pp1 = deltas[npts - 4][0]
        pp2 = deltas[npts - 3][0]
        new_adv[gid] = max(0, round(adv[gid] + pp2 - pp1))
        if g is None:
            continue
        if g["simple"]:
            g["x"] = [round(x + deltas[i][0] - pp1) for i, x in enumerate(g["x"])]
            g["y"] = [round(y + deltas[i][1]) for i, y in enumerate(g["y"])]
        else:
            for i, c in enumerate(g["comps"]):
                if c["flags"] & 2:  # Versatz in x/y (nicht Punktnummern)
                    c["dx"] = round(c["dx"] + deltas[i][0] - pp1)
                    c["dy"] = round(c["dy"] + deltas[i][1])

    # Begrenzungsrahmen (zusammengesetzte Glyphen rekursiv)
    points_cache = {}

    def points(gid, depth=0):
        if gid in points_cache:
            return points_cache[gid]
        g = glyphs[gid]
        if g is None or depth > 8:
            pts = []
        elif g["simple"]:
            pts = list(zip(g["x"], g["y"]))
        else:
            pts = []
            for c in g["comps"]:
                a, b, cc, d = transform(c)
                for x, y in points(c["gid"], depth + 1):
                    nx = x * a + y * cc
                    ny = x * b + y * d
                    pts.append((nx + (c["dx"] if c["flags"] & 2 else 0), ny + (c["dy"] if c["flags"] & 2 else 0)))
        points_cache[gid] = pts
        return pts

    new_lsb = list(lsb)
    xmin_all, ymin_all, xmax_all, ymax_all = 32767, 32767, -32768, -32768
    min_rsb, max_extent = 32767, -32768
    for gid, g in enumerate(glyphs):
        pts = points(gid)
        if g is None or not pts:
            if g is not None:
                g["bbox"] = (0, 0, 0, 0)
            new_lsb[gid] = 0
            continue
        x0 = int(min(p[0] for p in pts) // 1)
        y0 = int(min(p[1] for p in pts) // 1)
        x1 = int(-(-max(p[0] for p in pts) // 1))
        y1 = int(-(-max(p[1] for p in pts) // 1))
        g["bbox"] = (x0, y0, x1, y1)
        new_lsb[gid] = x0
        xmin_all, ymin_all = min(xmin_all, x0), min(ymin_all, y0)
        xmax_all, ymax_all = max(xmax_all, x1), max(ymax_all, y1)
        min_rsb = min(min_rsb, new_adv[gid] - x1)
        max_extent = max(max_extent, x1)

    glyf_out = bytearray()
    loca_out = []
    for g in glyphs:
        loca_out.append(len(glyf_out))
        raw = write_glyph(g)
        glyf_out += raw
        while len(glyf_out) % 4:
            glyf_out.append(0)
    loca_out.append(len(glyf_out))

    out_tables = {}
    for tag, raw in tables.items():
        if tag in DROP:
            continue
        out_tables[tag] = raw
    out_tables[b"glyf"] = bytes(glyf_out)
    out_tables[b"loca"] = struct.pack(">%dI" % len(loca_out), *loca_out)
    out_tables[b"hmtx"] = b"".join(struct.pack(">Hh", new_adv[i], new_lsb[i]) for i in range(num_glyphs))
    struct.pack_into(">H", hhea, 10, max(new_adv))
    struct.pack_into(">h", hhea, 12, min(new_lsb))
    struct.pack_into(">h", hhea, 14, min_rsb)
    struct.pack_into(">h", hhea, 16, max_extent)
    struct.pack_into(">H", hhea, 34, num_glyphs)
    out_tables[b"hhea"] = bytes(hhea)
    struct.pack_into(">hhhh", head, 36, xmin_all, ymin_all, xmax_all, ymax_all)
    struct.pack_into(">h", head, 50, 1)
    struct.pack_into(">I", head, 8, 0)
    out_tables[b"head"] = head
    # Glyphennamen braucht niemand: post Version 3
    post = bytearray(out_tables[b"post"][:32])
    struct.pack_into(">I", post, 0, 0x00030000)
    out_tables[b"post"] = bytes(post)
    os2 = bytearray(out_tables[b"OS/2"])
    struct.pack_into(">H", os2, 4, int(weight))
    out_tables[b"OS/2"] = bytes(os2)

    # Schrift zusammensetzen
    tags = sorted(out_tables)
    n = len(tags)
    es = max(k for k in range(16) if 2 ** k <= n)
    header = struct.pack(">IHHHH", 0x00010000, n, 16 * 2 ** es, es, n * 16 - 16 * 2 ** es)
    offset = 12 + 16 * n
    directory = bytearray()
    body = bytearray()

    def checksum(b):
        b = b + b"\0" * ((4 - len(b) % 4) % 4)
        return sum(struct.unpack(">%dI" % (len(b) // 4), b)) & 0xFFFFFFFF

    head_pos = 0
    for tag in tags:
        raw = bytes(out_tables[tag])
        if tag == b"head":
            head_pos = offset + len(body)
        directory += struct.pack(">4sIII", tag, checksum(raw), offset + len(body), len(raw))
        body += raw
        while len(body) % 4:
            body.append(0)
    font = bytearray(header + directory + body)
    struct.pack_into(">I", font, head_pos + 8, (0xB1B0AFBA - checksum(bytes(font))) & 0xFFFFFFFF)
    with open(dst, "wb") as f:
        f.write(font)
    return coords


# ------------------------------------------------------------------ Definitionen und Sprites

# Schnitte: Datei, Größe (GUI-Pixel pro Geviert). „caps“ ist die kleinere Antiqua für Kapitälchen.
FACES = {
    "roman": ("cormorant-garamond-medium.ttf", 11.0),
    "italic": ("cormorant-garamond-medium-italic.ttf", 11.0),
    "caps": ("cormorant-garamond-medium.ttf", 8.0),
}
# Bildschirmpixel pro GUI-Pixel, für die es eine scharfe Fassung gibt (Serif.OVERSAMPLE)
OVERSAMPLE = [1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0]


def write_json(path, data):
    with open(path, "w", newline="\n", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main(assets):
    repo = Path(__file__).resolve().parents[3]
    src = repo / "launcher" / "public" / "fonts"
    font_dir = assets / "font"
    (font_dir / "salumeria").mkdir(parents=True, exist_ok=True)
    instance(src / "cormorant-garamond.ttf", font_dir / "cormorant-garamond-medium.ttf", 500)
    instance(src / "cormorant-garamond-italic.ttf", font_dir / "cormorant-garamond-medium-italic.ttf", 500)
    shutil.copyfile(src / "cormorant-garamond-OFL.txt", font_dir / "cormorant-garamond-OFL.txt")
    for old in (font_dir / "salumeria").glob("*.json"):
        old.unlink()
    for face, (file, size) in FACES.items():
        for o in OVERSAMPLE:
            write_json(font_dir / "salumeria" / f"{face}_{round(o * 10)}.json", {"providers": [
                {"type": "ttf", "file": f"salami:{file}", "size": size, "oversample": o},
                # Zeichen, die Cormorant nicht hat (⏎, ✔ …), aus Minecrafts Schrift
                {"type": "reference", "id": "minecraft:default"},
            ]})

    from PIL import Image

    sprites = assets / "textures" / "gui" / "sprites" / "salumeria"
    sprites.mkdir(parents=True, exist_ok=True)
    # Logo: 22 GUI-Pixel, doppelte Auflösung
    logo = Image.open(repo / "launcher" / "public" / "logo.png").convert("RGBA")
    logo.resize((44, 44), Image.LANCZOS).save(sprites / "logo.png")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
