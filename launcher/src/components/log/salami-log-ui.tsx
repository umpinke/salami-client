/**
 * Gemeinsame Bausteine der Salami-Log-Ansichten (Profil-Tab „Logs“ und Log-Fenster):
 * Stufenfilter als Textreiter, Suchfeld mit Trefferzahl, Log-Zeile mit Farbleiste und
 * Zeilennummer, Statuszeile und flache Werkzeugknöpfe – Haarlinien statt Kästen.
 */
import type React from "react";
import { Fragment } from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";
import { hexAlpha, readableOn } from "../ui/design-system";

export type LevelKey = "ERROR" | "WARN" | "INFO" | "DEBUG" | "TRACE";
export const LEVEL_ORDER: LevelKey[] = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"];

/** Farbe je Stufe; Fehler und Warnungen färben zusätzlich die Zeile leicht ein. */
export const LEVEL_STYLE: Record<LevelKey, { color: string; rowTint?: string; text?: string }> = {
  ERROR: { color: "#f87171", rowTint: "rgba(248, 113, 113, 0.09)", text: "text-red-200" },
  WARN: { color: "#fbbf24", rowTint: "rgba(251, 191, 36, 0.06)", text: "text-amber-100" },
  INFO: { color: "#7dd3fc" },
  DEBUG: { color: "#a1a1aa" },
  TRACE: { color: "#c4b5fd" },
};

export type LevelCounts = Partial<Record<LevelKey, number>>;

function isLevelKey(level: string | undefined | null): level is LevelKey {
  return !!level && (LEVEL_ORDER as string[]).includes(level);
}

/** Stufenfilter als Textreiter: aktive Stufen weiß mit Strich in ihrer Farbe, dahinter die Anzahl. */
export function LevelChips({
  enabled,
  counts,
  onToggle,
}: {
  enabled: Partial<Record<LevelKey, boolean>>;
  counts: LevelCounts;
  onToggle: (level: LevelKey) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-end gap-x-5 gap-y-1">
      {LEVEL_ORDER.map((level) => {
        const active = !!enabled[level];
        const { color } = LEVEL_STYLE[level];
        const count = counts[level] ?? 0;
        return (
          <button
            key={level}
            type="button"
            onClick={() => onToggle(level)}
            aria-pressed={active}
            title={t(`salami_logs.toggle_${active ? "hide" : "show"}`, { level: t(`salami_logs.level.${level}`) })}
            className={cn(
              "relative flex flex-shrink-0 items-baseline gap-1.5 pb-2.5 font-minecraft text-xl lowercase leading-none transition-colors duration-150",
              active ? "text-white" : "text-white/35 hover:text-white/70",
            )}
            style={{ borderRadius: 0 }}
          >
            {t(`salami_logs.level.${level}`)}
            <span
              className="text-xs tabular-nums"
              style={{ color: active && count > 0 ? color : "rgba(255,255,255,0.3)" }}
            >
              {count}
            </span>
            {active && <span className="absolute inset-x-0 -bottom-px h-[3px]" style={{ backgroundColor: color }} />}
          </button>
        );
      })}
    </div>
  );
}

/** Suchfeld mit Trefferzahl und Löschen-Knopf. */
export function LogSearchField({
  value,
  onChange,
  matches,
  className,
}: {
  value: string;
  onChange: (value: string) => void;
  matches?: number;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <label
      className={cn(
        "flex h-9 items-center gap-2 border border-white/[0.08] bg-black/25 px-3 transition-colors focus-within:border-white/25",
        className,
      )}
      style={{ borderRadius: "var(--border-radius)" }}
    >
      <Icon icon="solar:magnifer-linear" className="h-4 w-4 shrink-0 text-white/40" />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t("salami_logs.search")}
        className="min-w-0 flex-1 bg-transparent text-sm text-white outline-none placeholder:text-white/30"
      />
      {value && (
        <>
          {matches !== undefined && (
            <span className="shrink-0 text-[11px] tabular-nums text-white/45">
              {t("salami_logs.matches", { count: matches })}
            </span>
          )}
          <button
            type="button"
            onClick={() => onChange("")}
            className="shrink-0 text-white/40 hover:text-white/80"
            aria-label={t("salami_logs.clear_search")}
          >
            <Icon icon="solar:close-circle-bold" className="h-4 w-4" />
          </button>
        </>
      )}
    </label>
  );
}

/** Text mit hervorgehobenen Suchtreffern. */
export function Highlight({ text, query }: { text: string; query: string }) {
  const accent = useThemeStore((s) => s.accentColor.value);
  const q = query.trim();
  if (!q) return <>{text}</>;
  const lower = text.toLowerCase();
  const needle = q.toLowerCase();
  const parts: React.ReactNode[] = [];
  let from = 0;
  let hit = lower.indexOf(needle, from);
  while (hit !== -1) {
    if (hit > from) parts.push(<Fragment key={`t${from}`}>{text.slice(from, hit)}</Fragment>);
    parts.push(
      <mark key={`m${hit}`} className="px-0.5 text-white" style={{ backgroundColor: `${accent}66` }}>
        {text.slice(hit, hit + needle.length)}
      </mark>,
    );
    from = hit + needle.length;
    hit = lower.indexOf(needle, from);
  }
  if (from < text.length) parts.push(<Fragment key={`t${from}`}>{text.slice(from)}</Fragment>);
  return <>{parts}</>;
}

/** Eine Log-Zeile: Farbleiste, optional Zeilennummer, Uhrzeit, optional Thread, Nachricht. */
export function LogRowView({
  level,
  timestamp,
  thread,
  message,
  showThread,
  wrap = true,
  query = "",
  selected = false,
  lineNumber,
  rowProps,
}: {
  level?: string | null;
  timestamp?: string | null;
  thread?: string | null;
  message: string;
  showThread: boolean;
  wrap?: boolean;
  query?: string;
  selected?: boolean;
  /** Laufende Nummer am linken Rand (Log-Fenster). */
  lineNumber?: number;
  rowProps?: React.HTMLAttributes<HTMLDivElement> & Record<string, unknown>;
}) {
  const accent = useThemeStore((s) => s.accentColor.value);
  const style = isLevelKey(level) ? LEVEL_STYLE[level] : null;
  const continuation = !timestamp;
  return (
    <div
      {...rowProps}
      className={cn("group flex items-stretch gap-3 pr-3", !selected && "hover:bg-white/[0.04]")}
      style={{ backgroundColor: selected ? `${accent}33` : style?.rowTint }}
    >
      <span
        className="w-[3px] shrink-0"
        style={{ backgroundColor: style?.rowTint ? style.color : "transparent" }}
      />
      {lineNumber !== undefined && (
        <span className="w-9 shrink-0 select-none py-[3px] text-right font-mono text-[11px] tabular-nums leading-[1.75] text-white/[0.18] transition-colors group-hover:text-white/45">
          {lineNumber}
        </span>
      )}
      <span className="w-[62px] shrink-0 select-none py-[3px] font-mono text-[11px] tabular-nums leading-[1.75] text-white/35">
        {timestamp ?? ""}
      </span>
      <span
        className={cn(
          "min-w-0 flex-1 py-[3px] font-mono text-[12.5px] leading-[1.55]",
          wrap ? "whitespace-pre-wrap break-words" : "overflow-x-auto whitespace-pre",
          continuation && "pl-4 text-white/55",
          !continuation && (style?.text ?? "text-white/85"),
        )}
      >
        {showThread && thread && !continuation && (
          <span className="mr-2 select-none text-[11px] text-white/35">{thread} ›</span>
        )}
        <Highlight text={message} query={query} />
      </span>
    </div>
  );
}

/** Flacher Knopf mit Symbol und Text für Log-Werkzeugleisten; `primary` füllt ihn mit der Akzentfarbe. */
export function LogToolButton({
  icon,
  label,
  onClick,
  disabled,
  busy,
  active,
  primary,
  title,
}: {
  icon: string;
  label?: string;
  onClick?: () => void;
  disabled?: boolean;
  busy?: boolean;
  active?: boolean;
  primary?: boolean;
  title?: string;
}) {
  const accent = useThemeStore((s) => s.accentColor.value);
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || busy}
      title={title ?? label}
      aria-label={title ?? label}
      className={cn(
        "flex h-8 items-center gap-1.5 px-2.5 font-minecraft text-sm lowercase transition-[color,background-color,filter] duration-150 disabled:opacity-40",
        primary
          ? "hover:brightness-110"
          : active
            ? "text-white"
            : "text-white/55 hover:bg-white/[0.06] hover:text-white",
      )}
      style={
        primary
          ? { backgroundColor: accent, color: readableOn(accent) }
          : active
            ? { backgroundColor: hexAlpha(accent, 0.16) }
            : undefined
      }
    >
      <Icon icon={busy ? "svg-spinners:ring-resize" : icon} className="h-4 w-4 shrink-0" />
      {label && <span className="whitespace-nowrap">{label}</span>}
    </button>
  );
}

/** Zusammenfassung „1.234 Zeilen · 3 Fehler · 12 Warnungen“. */
export function LogSummary({ lines, counts }: { lines: number; counts: LevelCounts }) {
  const { t, i18n } = useTranslation();
  const fmt = new Intl.NumberFormat(i18n.language || undefined);
  const errors = counts.ERROR ?? 0;
  const warnings = counts.WARN ?? 0;
  return (
    <div className="flex items-center gap-3 text-xs text-white/45">
      <span className="tabular-nums">{t("salami_logs.lines", { count: lines, formatted: fmt.format(lines) })}</span>
      {errors > 0 && (
        <span className="flex items-center gap-1.5 tabular-nums text-red-300/90">
          <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: LEVEL_STYLE.ERROR.color }} />
          {t("salami_logs.errors", { count: errors })}
        </span>
      )}
      {warnings > 0 && (
        <span className="flex items-center gap-1.5 tabular-nums text-amber-200/90">
          <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: LEVEL_STYLE.WARN.color }} />
          {t("salami_logs.warnings", { count: warnings })}
        </span>
      )}
    </div>
  );
}

/** Zählt Zeilen je Stufe (nur Zeilen mit eigener Stufe). */
export function countLevels(levels: Iterable<string | undefined | null>): LevelCounts {
  const counts: LevelCounts = {};
  for (const level of levels) {
    if (isLevelKey(level)) counts[level] = (counts[level] ?? 0) + 1;
  }
  return counts;
}

/** Leerer Zustand: blasses Logo, Titel in der Schriftart des Launchers, Hinweis darunter. */
export function LogEmptyState({ icon, title, hint }: { icon?: string; title: string; hint?: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 px-6 text-center">
      {icon ? (
        <Icon icon={icon} className="mb-1 h-9 w-9 text-white/20" />
      ) : (
        <img src="/salami-logo-64.png" alt="" className="mb-1 h-10 w-10 rounded-full opacity-40 grayscale" />
      )}
      <p className="font-minecraft text-2xl lowercase leading-none text-white/75">{title}</p>
      {hint && <p className="max-w-sm text-xs text-white/40">{hint}</p>}
    </div>
  );
}
