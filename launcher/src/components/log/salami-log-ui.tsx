/**
 * Gemeinsame Bausteine der Salami-Log-Ansichten (Profil-Tab „Logs“ und Log-Fenster):
 * Filter-Chips mit Zählern, Suchfeld mit Trefferzahl, Log-Zeile mit Farbleiste und
 * Hervorhebung, Statuszeile und Werkzeugknöpfe.
 */
import type React from "react";
import { Fragment } from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { cn } from "../../lib/utils";
import { useThemeStore } from "../../store/useThemeStore";

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

/** Filter-Chips „● Fehler 3“; aktive Chips in der Farbe ihrer Stufe. */
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
    <div className="flex flex-wrap items-center gap-1.5">
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
              "flex h-8 items-center gap-2 rounded-full border px-3 text-xs transition-colors",
              active ? "text-white" : "border-white/[0.06] bg-white/[0.03] text-white/40 hover:text-white/70",
            )}
            style={active ? { backgroundColor: `${color}1f`, borderColor: `${color}55` } : undefined}
          >
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: active ? color : "rgba(255,255,255,0.25)" }} />
            {t(`salami_logs.level.${level}`)}
            <span className={cn("tabular-nums", active ? "text-white/60" : "text-white/30")}>{count}</span>
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
        "flex h-9 items-center gap-2 rounded-full border border-white/[0.08] bg-black/30 px-3 focus-within:border-white/25",
        className,
      )}
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
      <mark key={`m${hit}`} className="rounded px-0.5 text-white" style={{ backgroundColor: `${accent}66` }}>
        {text.slice(hit, hit + needle.length)}
      </mark>,
    );
    from = hit + needle.length;
    hit = lower.indexOf(needle, from);
  }
  if (from < text.length) parts.push(<Fragment key={`t${from}`}>{text.slice(from)}</Fragment>);
  return <>{parts}</>;
}

/** Eine Log-Zeile: Farbleiste, Uhrzeit-Spalte, optional Thread, Nachricht. */
export function LogRowView({
  level,
  timestamp,
  thread,
  message,
  showThread,
  wrap = true,
  query = "",
  selected = false,
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
  rowProps?: React.HTMLAttributes<HTMLDivElement> & Record<string, unknown>;
}) {
  const accent = useThemeStore((s) => s.accentColor.value);
  const style = isLevelKey(level) ? LEVEL_STYLE[level] : null;
  const continuation = !timestamp;
  return (
    <div
      {...rowProps}
      className={cn("group flex items-stretch gap-3 rounded-md pr-2", !selected && "hover:bg-white/[0.04]")}
      style={{ backgroundColor: selected ? `${accent}33` : style?.rowTint }}
    >
      <span
        className="w-[3px] shrink-0 rounded-full"
        style={{ backgroundColor: style?.rowTint ? style.color : "transparent" }}
      />
      <span className="w-[62px] shrink-0 select-none py-[3px] text-[11px] tabular-nums text-white/30">
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
          <span className="mr-2 select-none rounded bg-white/[0.06] px-1.5 py-px align-middle text-[10px] text-white/45">
            {thread}
          </span>
        )}
        <Highlight text={message} query={query} />
      </span>
    </div>
  );
}

/** Kleiner Knopf mit Symbol und Text für Log-Werkzeugleisten. */
export function LogToolButton({
  icon,
  label,
  onClick,
  disabled,
  busy,
  active,
  title,
}: {
  icon: string;
  label?: string;
  onClick?: () => void;
  disabled?: boolean;
  busy?: boolean;
  active?: boolean;
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
        "flex h-8 items-center gap-1.5 rounded-lg border px-2.5 text-xs transition-colors disabled:opacity-40",
        active ? "text-white" : "border-white/[0.06] bg-white/[0.04] text-white/70 hover:bg-white/[0.08] hover:text-white",
      )}
      style={active ? { backgroundColor: `${accent}33`, borderColor: `${accent}66` } : undefined}
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

/** Leerer Zustand im Salami-Look. */
export function LogEmptyState({ icon, title, hint }: { icon?: string; title: string; hint?: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-white/[0.06] bg-white/[0.04]">
        {icon ? (
          <Icon icon={icon} className="h-7 w-7 text-white/40" />
        ) : (
          <img src="/salami-logo-64.png" alt="" className="h-8 w-8 rounded-full opacity-70" />
        )}
      </div>
      <p className="text-sm text-white/70">{title}</p>
      {hint && <p className="max-w-sm text-xs text-white/40">{hint}</p>}
    </div>
  );
}
