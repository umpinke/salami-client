import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { builtinCapeImage, saveCape, type SalamiCape } from "../../services/salami-cape-service";
import { Cape3D } from "./Cape3D";
import { EDITOR_AREAS, TEXTURE_HEIGHT, TEXTURE_WIDTH, areaAt } from "./cape-layout";
import "./i18n";

/** Neues Cape (`cape` leer) oder ein vorhandenes bearbeiten. */
export interface CapeEditorTarget {
  cape?: SalamiCape;
}

interface CapeEditorProps {
  target: CapeEditorTarget;
  onClose: () => void;
  onSaved: (cape: SalamiCape) => void;
}

type Tool = "pencil" | "eraser" | "fill" | "picker";
type Rgba = [number, number, number, number];

const W = TEXTURE_WIDTH;
const H = TEXTURE_HEIGHT;
const ZOOM = 11;
const MAX_UNDO = 50;
const PALETTE = [
  "#b23a3a", "#d06060", "#7a2425", "#f3e3d3", "#dec4b4", "#2b1d1a", "#7a4a35", "#4a281e",
  "#ffffff", "#aaaaaa", "#555555", "#000000", "#ff5555", "#ffaa00", "#ffff55", "#55ff55",
  "#55ffff", "#5555ff", "#ff55ff", "#c4966a",
];
const STARTERS = ["salami", "pepperoni", "board"] as const;

const hexToRgba = (hex: string): Rgba => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
  255,
];
const rgbaToHex = ([r, g, b]: Rgba) => `#${[r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("")}`;

function forEachCell([rx, ry, w, h]: readonly [number, number, number, number], fn: (x: number, y: number) => void) {
  for (let y = ry; y < ry + h; y++) for (let x = rx; x < rx + w; x++) fn(x, y);
}

/** Lädt ein Bild auf 64x32 (größere Capes werden pixelgenau verkleinert). */
async function loadPixels(src: string): Promise<{ data: Uint8ClampedArray; downscaled: boolean }> {
  const image = new Image();
  image.src = src;
  await image.decode();
  const canvas = document.createElement("canvas");
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext("2d")!;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(image, 0, 0, W, H);
  return { data: ctx.getImageData(0, 0, W, H).data, downscaled: image.naturalWidth > W };
}

/** Pixel-Editor für Capes im Minecraft-Layout (64x32). */
export function CapeEditor({ target, onClose, onSaved }: CapeEditorProps) {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pixels = useRef(new Uint8ClampedArray(W * H * 4));
  const undoStack = useRef<Uint8ClampedArray[]>([]);
  const drawing = useRef<{ erase: boolean; last: [number, number] | null } | null>(null);
  const [name, setName] = useState(target.cape?.name ?? "Mein Cape");
  const [tool, setTool] = useState<Tool>("pencil");
  const [color, setColor] = useState(PALETTE[0]);
  const [mirror, setMirror] = useState(false);
  const [showAreas, setShowAreas] = useState(true);
  const [preview, setPreview] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [, forceRender] = useState(0);

  const toDataUrl = useCallback(() => {
    const canvas = document.createElement("canvas");
    canvas.width = W;
    canvas.height = H;
    canvas.getContext("2d")!.putImageData(new ImageData(pixels.current.slice(), W, H), 0, 0);
    return canvas.toDataURL("image/png");
  }, []);

  const redraw = useCallback(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (showAreas) {
      for (const area of EDITOR_AREAS) {
        ctx.fillStyle = area.color;
        forEachCell(area.rect, (x, y) => {
          if (area.contains(x, y)) ctx.fillRect(x * ZOOM, y * ZOOM, ZOOM, ZOOM);
        });
      }
    }
    const data = pixels.current;
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const i = (y * W + x) * 4;
        if (data[i + 3] === 0) continue;
        ctx.fillStyle = `rgba(${data[i]}, ${data[i + 1]}, ${data[i + 2]}, ${data[i + 3] / 255})`;
        ctx.fillRect(x * ZOOM, y * ZOOM, ZOOM, ZOOM);
      }
    }
    ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let x = 0; x <= W; x++) {
      ctx.moveTo(x * ZOOM + 0.5, 0);
      ctx.lineTo(x * ZOOM + 0.5, H * ZOOM);
    }
    for (let y = 0; y <= H; y++) {
      ctx.moveTo(0, y * ZOOM + 0.5);
      ctx.lineTo(W * ZOOM, y * ZOOM + 0.5);
    }
    ctx.stroke();
    if (showAreas) {
      ctx.strokeStyle = "rgba(255, 255, 255, 0.35)";
      ctx.font = "600 11px Fredoka, sans-serif";
      ctx.fillStyle = "rgba(255, 255, 255, 0.55)";
      const edge = (x0: number, y0: number, x1: number, y1: number) => {
        ctx.moveTo(x0, y0);
        ctx.lineTo(x1, y1);
      };
      ctx.beginPath();
      for (const area of EDITOR_AREAS) {
        // Umriss: Kanten zu Pixeln außerhalb der Fläche (funktioniert auch für die Flügelform)
        forEachCell(area.rect, (x, y) => {
          if (!area.contains(x, y)) return;
          const left = x * ZOOM + 0.5;
          const top = y * ZOOM + 0.5;
          const right = (x + 1) * ZOOM - 0.5;
          const bottom = (y + 1) * ZOOM - 0.5;
          if (!area.contains(x, y - 1)) edge(left, top, right, top);
          if (!area.contains(x, y + 1)) edge(left, bottom, right, bottom);
          if (!area.contains(x - 1, y)) edge(left, top, left, bottom);
          if (!area.contains(x + 1, y)) edge(right, top, right, bottom);
        });
      }
      ctx.stroke();
      for (const { rect: [x, y, , h], labelKey } of EDITOR_AREAS) {
        if (labelKey) ctx.fillText(t(labelKey), x * ZOOM + 4, (y + h) * ZOOM - 5);
      }
    }
  }, [showAreas, t]);

  const refreshPreview = useCallback(() => setPreview(toDataUrl()), [toDataUrl]);

  // Startbild laden
  const loadFrom = useCallback(
    async (src: string | null) => {
      if (src) {
        try {
          const { data, downscaled } = await loadPixels(src);
          pixels.current = new Uint8ClampedArray(data);
          if (downscaled) toast(t("salami.capes.editor.downscaled"));
        } catch {
          pixels.current = new Uint8ClampedArray(W * H * 4);
        }
      } else {
        pixels.current = new Uint8ClampedArray(W * H * 4);
      }
      undoStack.current = [];
      redraw();
      refreshPreview();
      forceRender((n) => n + 1);
    },
    [redraw, refreshPreview, t],
  );

  useEffect(() => {
    void loadFrom(target.cape?.image ?? null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    redraw();
  }, [redraw]);

  const pushUndo = () => {
    undoStack.current.push(pixels.current.slice());
    if (undoStack.current.length > MAX_UNDO) undoStack.current.shift();
  };

  const setPixel = (x: number, y: number, rgba: Rgba) => {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    pixels.current.set(rgba, (y * W + x) * 4);
    if (mirror) {
      const area = areaAt(x, y);
      if (area?.mirror) {
        const [ax, , aw] = area.rect;
        const mx = ax + aw - 1 - (x - ax);
        pixels.current.set(rgba, (y * W + mx) * 4);
      }
    }
  };

  const plotLine = (from: [number, number], to: [number, number], rgba: Rgba) => {
    // Bresenham – keine Lücken bei schnellen Mausbewegungen
    let [x0, y0] = from;
    const [x1, y1] = to;
    const dx = Math.abs(x1 - x0);
    const dy = -Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1;
    const sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      setPixel(x0, y0, rgba);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x0 += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y0 += sy;
      }
    }
  };

  const floodFill = (x: number, y: number, rgba: Rgba) => {
    const data = pixels.current;
    const start = (y * W + x) * 4;
    const target = Array.from(data.slice(start, start + 4));
    if (target.every((v, i) => v === rgba[i])) return;
    // Füllen bleibt in der angeklickten Fläche (außerhalb aller Flächen: im freien Bereich)
    const area = areaAt(x, y);
    const sameArea = (px: number, py: number) => px >= 0 && py >= 0 && px < W && py < H && areaAt(px, py) === area;
    const stack: [number, number][] = [[x, y]];
    while (stack.length) {
      const [px, py] = stack.pop()!;
      if (!sameArea(px, py)) continue;
      const i = (py * W + px) * 4;
      if (data[i] !== target[0] || data[i + 1] !== target[1] || data[i + 2] !== target[2] || data[i + 3] !== target[3]) continue;
      data.set(rgba, i);
      stack.push([px + 1, py], [px - 1, py], [px, py + 1], [px, py - 1]);
    }
  };

  const cellOf = (event: React.PointerEvent<HTMLCanvasElement>): [number, number] => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = Math.floor(((event.clientX - rect.left) / rect.width) * W);
    const y = Math.floor(((event.clientY - rect.top) / rect.height) * H);
    return [Math.min(W - 1, Math.max(0, x)), Math.min(H - 1, Math.max(0, y))];
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLCanvasElement>) => {
    event.preventDefault();
    const [x, y] = cellOf(event);
    const erase = event.button === 2 || tool === "eraser";
    if (tool === "picker" && !erase) {
      const i = (y * W + x) * 4;
      if (pixels.current[i + 3] > 0) setColor(rgbaToHex(Array.from(pixels.current.slice(i, i + 4)) as Rgba));
      setTool("pencil");
      return;
    }
    pushUndo();
    if (tool === "fill" && !erase) {
      floodFill(x, y, hexToRgba(color));
      redraw();
      refreshPreview();
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    drawing.current = { erase, last: [x, y] };
    setPixel(x, y, erase ? [0, 0, 0, 0] : hexToRgba(color));
    redraw();
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const stroke = drawing.current;
    if (!stroke) return;
    const cell = cellOf(event);
    plotLine(stroke.last ?? cell, cell, stroke.erase ? [0, 0, 0, 0] : hexToRgba(color));
    stroke.last = cell;
    redraw();
  };

  const handlePointerUp = () => {
    if (!drawing.current) return;
    drawing.current = null;
    refreshPreview();
  };

  const undo = () => {
    const previous = undoStack.current.pop();
    if (!previous) return;
    pixels.current = previous;
    redraw();
    refreshPreview();
    forceRender((n) => n + 1);
  };

  const clear = () => {
    pushUndo();
    pixels.current = new Uint8ClampedArray(W * H * 4);
    redraw();
    refreshPreview();
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z") {
        e.preventDefault();
        undo();
      } else if (e.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const save = async () => {
    setSaving(true);
    try {
      const cape = await saveCape(target.cape?.id ?? null, name, toDataUrl());
      onSaved(cape);
    } catch (error) {
      toast.error(t("salami.capes.error", { error: error instanceof Error ? error.message : String(error) }));
    } finally {
      setSaving(false);
    }
  };

  const tools: { id: Tool; icon: string; label: string }[] = [
    { id: "pencil", icon: "solar:pen-bold", label: t("salami.capes.editor.pencil") },
    { id: "eraser", icon: "solar:eraser-bold", label: t("salami.capes.editor.eraser") },
    { id: "fill", icon: "solar:paint-roller-bold", label: t("salami.capes.editor.fill") },
    { id: "picker", icon: "solar:pipette-bold", label: t("salami.capes.editor.picker") },
  ];

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-6 backdrop-blur-sm">
      <div className="flex max-h-full w-full max-w-[1180px] flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#1d1412] shadow-2xl">
        <header className="flex items-center gap-3 border-b border-white/10 px-5 py-3">
          <Icon icon="solar:pen-new-square-bold" className="h-6 w-6" style={{ color: accent.value }} />
          <h2 className="text-lg font-semibold text-white">{t("salami.capes.editor.title")}</h2>
          <input
            value={name}
            maxLength={40}
            onChange={(e) => setName(e.target.value)}
            aria-label={t("salami.capes.editor.name")}
            className="ml-2 w-56 rounded-lg bg-black/40 px-3 py-1.5 text-sm text-white outline-none focus:ring-2 focus:ring-white/20"
          />
          <div className="ml-auto flex gap-2">
            <button type="button" onClick={onClose} className="rounded-xl bg-white/10 px-4 py-2 text-sm text-white hover:bg-white/15">
              {t("salami.capes.editor.cancel")}
            </button>
            <button
              type="button"
              disabled={saving}
              onClick={() => void save()}
              className="rounded-xl px-4 py-2 text-sm font-semibold text-white hover:brightness-110 disabled:opacity-50"
              style={{ backgroundColor: accent.value }}
            >
              {t("salami.capes.editor.save")}
            </button>
          </div>
        </header>

        <div className="flex gap-5 overflow-auto p-5">
          <div className="flex flex-col gap-3">
            <canvas
              ref={canvasRef}
              width={W * ZOOM}
              height={H * ZOOM}
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={handlePointerUp}
              onPointerCancel={handlePointerUp}
              onContextMenu={(e) => e.preventDefault()}
              className="cursor-crosshair rounded-lg"
              style={{
                width: W * ZOOM,
                height: H * ZOOM,
                backgroundColor: "#2a201d",
                backgroundImage:
                  "linear-gradient(45deg, #332724 25%, transparent 25%), linear-gradient(-45deg, #332724 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #332724 75%), linear-gradient(-45deg, transparent 75%, #332724 75%)",
                backgroundSize: `${ZOOM * 2}px ${ZOOM * 2}px`,
                backgroundPosition: `0 0, 0 ${ZOOM}px, ${ZOOM}px -${ZOOM}px, -${ZOOM}px 0`,
              }}
            />
            <p className="max-w-[704px] text-xs text-white/55">{t("salami.capes.editor.hint")}</p>
          </div>

          <aside className="flex w-[300px] shrink-0 flex-col gap-4">
            <div className="h-[220px] overflow-hidden rounded-xl border border-white/10 bg-black/30">
              {preview && <Cape3D src={preview} className="h-full w-full" />}
            </div>

            <div className="grid grid-cols-4 gap-2">
              {tools.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  title={item.label}
                  aria-label={item.label}
                  aria-pressed={tool === item.id}
                  onClick={() => setTool(item.id)}
                  className={cn(
                    "flex h-11 items-center justify-center rounded-xl text-white transition-colors",
                    tool === item.id ? "" : "bg-white/5 hover:bg-white/10",
                  )}
                  style={tool === item.id ? { backgroundColor: `${accent.value}aa` } : undefined}
                >
                  <Icon icon={item.icon} className="h-5 w-5" />
                </button>
              ))}
            </div>

            <div className="flex items-center gap-3">
              <input
                type="color"
                value={color}
                onChange={(e) => setColor(e.target.value)}
                aria-label={t("salami.capes.editor.picker")}
                className="h-10 w-12 cursor-pointer rounded-lg border-0 bg-transparent p-0"
              />
              <span className="font-mono text-sm text-white/70">{color}</span>
            </div>
            <div className="grid grid-cols-10 gap-1.5">
              {PALETTE.map((swatch) => (
                <button
                  key={swatch}
                  type="button"
                  aria-label={swatch}
                  onClick={() => {
                    setColor(swatch);
                    if (tool === "eraser" || tool === "picker") setTool("pencil");
                  }}
                  className={cn("h-6 w-6 rounded-md border", color === swatch ? "border-white" : "border-white/10")}
                  style={{ backgroundColor: swatch }}
                />
              ))}
            </div>

            <div className="flex flex-col gap-2 text-sm text-white/85">
              <label className="flex cursor-pointer items-center gap-2">
                <input type="checkbox" checked={mirror} onChange={(e) => setMirror(e.target.checked)} />
                {t("salami.capes.editor.mirror")}
              </label>
              <label className="flex cursor-pointer items-center gap-2">
                <input type="checkbox" checked={showAreas} onChange={(e) => setShowAreas(e.target.checked)} />
                {t("salami.capes.editor.areas")}
              </label>
            </div>

            <div className="flex gap-2">
              <button type="button" onClick={undo} className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-white/5 py-2 text-sm text-white hover:bg-white/10">
                <Icon icon="solar:undo-left-round-bold" className="h-4 w-4" />
                {t("salami.capes.editor.undo")}
              </button>
              <button type="button" onClick={clear} className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-white/5 py-2 text-sm text-white hover:bg-white/10">
                <Icon icon="solar:trash-bin-minimalistic-bold" className="h-4 w-4" />
                {t("salami.capes.editor.clear")}
              </button>
            </div>

            <div>
              <div className="mb-1.5 text-xs uppercase tracking-wider text-white/50">{t("salami.capes.editor.start_from")}</div>
              <div className="flex flex-wrap gap-1.5">
                <button type="button" onClick={() => void loadFrom(null)} className="rounded-lg bg-white/5 px-2.5 py-1 text-xs text-white hover:bg-white/10">
                  {t("salami.capes.editor.blank")}
                </button>
                {STARTERS.map((starter) => (
                  <button
                    key={starter}
                    type="button"
                    onClick={() => void loadFrom(builtinCapeImage(starter))}
                    className="rounded-lg bg-white/5 px-2.5 py-1 text-xs text-white hover:bg-white/10"
                  >
                    {t(`salami.capes.${starter}`)}
                  </button>
                ))}
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
