"use client";

import { useCallback, useRef, useState } from "react";
import { BRAND } from "../../config/salami";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { useThemeStore } from "../../store/useThemeStore";
import { useMinecraftAuthStore } from "../../store/minecraft-auth-store";
import { useWelcomeStore } from "../../store/welcome-store";
import { useAddMinecraftAccount } from "../../hooks/useAddMinecraftAccount";
import { SURFACE, hexAlpha, kante, kopfband, readableOn } from "../ui/design-system";
import { DeliLabel } from "../../themes/salami/SalamiStage";

/** Salami-Feinkost: warmer Fenstergrund (wie AppLayout) statt kaltem Violett-Schwarz. */
const GROUND = "#15100E";

/** Selling points on the hero half. Icons come from the offline bundle. */
const HIGHLIGHTS = [
  { icon: "solar:widget-add-bold", key: "mods" },
  { icon: "solar:hanger-2-bold", key: "capes" },
  { icon: "solar:bolt-bold", key: "fps" },
  { icon: "solar:layers-bold", key: "versions" },
] as const;

/**
 * Full-screen greeting for a launcher start with no account linked.
 *
 * Sits above the whole app rather than replacing the routed tree, so the
 * launcher boots normally behind it and dismissing costs nothing. It covers
 * the custom titlebar, so it carries its own drag region and window controls —
 * otherwise the window could not be moved or closed from here.
 */
export function WelcomeScreen() {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor).value;
  const animationsEnabled = useThemeStore((s) => s.isBackgroundAnimationEnabled);
  const isLoading = useMinecraftAuthStore((s) => s.isLoading);
  const skip = useWelcomeStore((s) => s.skip);
  const { startLogin } = useAddMinecraftAccount();

  // Parallax: the artwork drifts a little toward wherever the pointer is.
  // The listener sits on the whole overlay so it responds no matter which half
  // the pointer is over.
  //
  // Written straight to CSS custom properties rather than React state — a
  // rerender of the whole screen on every mouse move would be wasteful, and
  // these values only ever feed a transform.
  const rootRef = useRef<HTMLDivElement>(null);
  const onPointerMove = useCallback(
    (e: React.MouseEvent) => {
      const el = rootRef.current;
      if (!el || !animationsEnabled) return;
      el.style.setProperty("--px", `${(e.clientX / window.innerWidth - 0.5) * 2}`);
      el.style.setProperty("--py", `${(e.clientY / window.innerHeight - 0.5) * 2}`);
    },
    [animationsEnabled],
  );

  // Staggered entrance. Deliberately a keyframe animation over a base style
  // that is already visible, rather than a JS flag driving opacity 0 -> 1:
  // if the animation never runs — reduced motion, a throttled compositor, a
  // webview that mounts while off screen — the element is simply there. Gating
  // visibility on an animation completing risks a permanently blank first
  // screen, which is the one screen that must never fail to draw.
  const rise = (delayMs: number) =>
    ({
      animation: `welcome-rise 550ms cubic-bezier(0.22,1,0.36,1) ${delayMs}ms backwards`,
    }) as React.CSSProperties;

  const handleWindow = async (action: "minimize" | "toggleMaximize" | "close") => {
    const mod = await import("@tauri-apps/api/window").catch(() => null);
    if (!mod) return;
    const win = mod.getCurrentWindow();
    if (action === "minimize") await win.minimize();
    else if (action === "toggleMaximize") await win.toggleMaximize();
    else await win.close();
  };

  return (
    <div
      ref={rootRef}
      onMouseMove={onPointerMove}
      className="welcome-screen fixed inset-0 z-[900] flex select-none"
      style={{ ["--px" as string]: 0, ["--py" as string]: 0, backgroundColor: GROUND }}
    >
      <style>{`
        @keyframes welcome-rise {
          from { opacity: 0; transform: translateY(12px); }
          to   { opacity: 1; transform: none; }
        }
        /* Slow ambient drift so the hero breathes even with the pointer still. */
        @keyframes welcome-drift {
          0%, 100% { transform: scale(1.04) translate3d(0, 0, 0); }
          50%      { transform: scale(1.08) translate3d(-1.2%, -0.8%, 0); }
        }
        @media (prefers-reduced-motion: reduce) {
          .welcome-art { animation: none !important; }
        }
        /* globals.css hält im Hintergrund (.salami-inactive) alle Animationen an. Startet der
           Launcher ohne Fokus, blieben die Einblendungen sonst auf ihrem ersten Bild stehen –
           wegen "backwards" also unsichtbar. Die kurzen Einblendungen laufen deshalb immer. */
        .welcome-screen.welcome-screen [style*="welcome-rise"] { animation-play-state: running !important; }
        /* globals.css forces every Tailwind rounding utility — rounded-full
           included — to var(--border-radius) !important, which is 0px by
           default (the "Border Radius" setting, Minecraft-style square). A
           utility class therefore cannot round anything here. This rule
           needs more specificity than BOTH that override and
           .radius-themed button:not(.no-radius), which is what keeps buttons
           square even when the utility override is beaten. The doubled class
           gets there without enumerating element types, and leaves the global
           token everything else still follows untouched. */
        .welcome-screen .welcome-round.welcome-round { border-radius: 14px !important; }
      `}</style>

      {/* Titlebar strip: drag region + controls, floating over both halves. */}
      <div
        data-tauri-drag-region
        className="absolute top-0 inset-x-0 h-10 z-30 flex items-center justify-end px-4 gap-3"
      >
        {([
          ["minimize", "pixel:minus-solid", "window.minimize"],
          ["toggleMaximize", "pixel:expand-solid", "window.maximize"],
          ["close", "pixel:window-close-solid", "window.close"],
        ] as const).map(([action, icon, labelKey]) => (
          <button
            key={action}
            onClick={() => handleWindow(action)}
            title={t(labelKey)}
            className={`w-5 h-5 flex items-center justify-center text-white/50 transition-colors ${
              action === "close" ? "hover:text-red-500" : "hover:text-white"
            }`}
          >
            <Icon icon={icon} className="w-4 h-4" />
          </button>
        ))}
      </div>

      {/* ── Hero half ──────────────────────────────────────────────────── */}
      <div className="relative hidden md:flex flex-col justify-between flex-1 min-w-0 overflow-hidden p-12">
        {/* Layer 1 — the artwork, pushed far back: dimmed, desaturated and
            off-centred so it reads as texture under the copy rather than as a
            competing picture. Follows the pointer. */}
        <div
          aria-hidden
          className="absolute inset-0 overflow-hidden"
          style={{
            transform: "translate3d(calc(var(--px) * 20px), calc(var(--py) * 15px), 0)",
            transition: "transform 140ms ease-out",
          }}
        >
          <div
            className="welcome-art absolute inset-0 bg-cover opacity-[0.55] saturate-[0.9]"
            style={{
              backgroundImage: "url(/welcome-background.jpg)",
              backgroundPosition: "46% 52%",
              animation: "welcome-drift 18s ease-in-out infinite",
            }}
          />
        </div>

        {/* Layer 2 — warm accent bloom, kept soft (no neon). Stays put; only the artwork moves. */}
        <div
          aria-hidden
          className="absolute inset-0 pointer-events-none"
          style={{
            background: `radial-gradient(85% 65% at 14% 86%, ${hexAlpha(accent, 0.24)} 0%, transparent 62%)`,
          }}
        />

        {/* Layer 3 — fixed vignette. Never moves: it is what guarantees the copy
            keeps its contrast wherever the parallax happens to land. */}
        <div
          aria-hidden
          className="absolute inset-0 pointer-events-none"
          style={{
            background:
              `linear-gradient(180deg, ${hexAlpha(GROUND, 0.8)} 0%, transparent 28%, ${GROUND} 100%),` +
              `linear-gradient(90deg, transparent 55%, ${GROUND} 100%)`,
          }}
        />

        {/* The wordmark is type, not an image — the same treatment the app
            header uses. The painted logo asset is not the current brand. */}
        <div className="relative z-10" style={rise(60)}>
          <span className="font-minecraft text-3xl text-white text-shadow">
            {BRAND.name}
          </span>
        </div>

        <div className="relative z-10">
          <h1
            className="font-minecraft normal-case tracking-normal text-white leading-[0.98] text-[clamp(2.5rem,4.4vw,4.25rem)] max-w-[16ch]"
            style={rise(140)}
          >
            {t("welcome.hero.line1")}
            <br />
            <span style={{ color: accent }}>{t("welcome.hero.line2")}</span>
          </h1>

          {/* Salami: Vorzüge als Etiketten wie an der Feinkosttheke */}
          <div className="mt-9 flex flex-wrap gap-2.5 max-w-[32rem]">
            {HIGHLIGHTS.map((h, i) => (
              <span key={h.key} style={rise(240 + i * 70)} className="inline-flex whitespace-nowrap">
                <DeliLabel>
                  <Icon icon={h.icon} className="h-4 w-4" />
                  {t(`welcome.highlight.${h.key}`)}
                </DeliLabel>
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* ── Sign-in half ───────────────────────────────────────────────── */}
      <div
        className="relative w-full md:w-[clamp(24rem,34%,30rem)] flex-shrink-0 flex flex-col justify-center px-10 py-12 border-l"
        style={{ backgroundColor: SURFACE.solid, borderColor: SURFACE.line, boxShadow: "-24px 0 60px -30px rgba(0,0,0,0.9)" }}
      >
        {/* Salami: Kopfband im Salami-Anschnitt statt Akzent-Schleier, damit die Spalte kein flacher Block ist */}
        <div aria-hidden className="salami-fat-spots absolute inset-x-0 top-0 h-3" style={kopfband(accent)} />

        <div className="relative flex flex-col items-center text-center">
          <img
            src="/logo.png"
            alt=""
            className="w-16 h-16 mb-6 drop-shadow-[0_3px_0_rgba(0,0,0,0.45)]"
            style={rise(80)}
          />

          <h2 className="text-4xl font-minecraft normal-case tracking-normal text-white" style={rise(160)}>
            {t("welcome.panel.title")}
          </h2>
          <p className="mt-3 text-sm text-white/60 leading-relaxed" style={rise(220)}>
            {t("welcome.panel.subtitle")}
          </p>

          <div className="w-full mt-9" style={rise(300)}>
            <SignInButton
              accent={accent}
              loading={isLoading}
              onClick={startLogin}
              label={t("welcome.panel.cta")}
              loadingLabel={t("welcome.panel.cta_loading")}
            />

            <button
              onClick={skip}
              className="welcome-round mt-3 w-full py-2.5 text-xs font-minecraft text-white/40 hover:text-white/75 hover:bg-white/5 transition-colors"
            >
              {t("welcome.panel.skip")}
            </button>
          </div>

          <p
            className="mt-8 pt-5 border-t w-full text-[11px] text-white/40 leading-relaxed"
            style={{ ...rise(380), borderColor: SURFACE.line }}
          >
            {t("welcome.panel.footnote")}
          </p>
        </div>
      </div>
    </div>
  );
}

interface SignInButtonProps {
  accent: string;
  loading: boolean;
  onClick: () => void;
  label: string;
  loadingLabel: string;
}

/**
 * The one button this whole screen exists for. Salami-Feinkost: full accent
 * fill with the hard bottom edge (`kante`) like every main action – on hover it
 * brightens, on press it sinks 1px onto that edge. No glow, no sweep.
 */
function SignInButton({ accent, loading, onClick, label, loadingLabel }: SignInButtonProps) {
  const [hovered, setHovered] = useState(false);
  const [pressed, setPressed] = useState(false);
  const active = hovered && !loading;

  return (
    <button
      onClick={onClick}
      disabled={loading}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => {
        setHovered(false);
        setPressed(false);
      }}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      className="relative w-full h-[58px] overflow-hidden font-minecraft text-lg disabled:cursor-wait"
      style={{
        backgroundColor: accent,
        color: readableOn(accent),
        filter: active ? "brightness(1.1)" : undefined,
        boxShadow: pressed ? kante(accent, 2) : kante(accent),
        transform: pressed ? "translateY(1px)" : "none",
        transition: "filter 150ms ease, transform 120ms ease, box-shadow 120ms ease",
      }}
    >
      <span className="relative flex items-center justify-center gap-3">
        <Icon
          icon={loading ? "svg-spinners:ring-resize" : "simple-icons:microsoft"}
          className="w-5 h-5"
        />
        {loading ? loadingLabel : label}
      </span>
    </button>
  );
}
