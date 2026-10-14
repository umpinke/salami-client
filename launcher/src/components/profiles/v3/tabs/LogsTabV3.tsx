"use client";

/**
 * LogsTabV3 — Log-Dateien eines Profils lesen (nur lesend; live mitlesen geht im Log-Fenster).
 * Salami-Look: Filter-Chips mit Zählern, Suche mit Trefferzahl, farbige Zeilen.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type React from "react";
import { Icon } from "@iconify/react";
import { useTranslation } from "react-i18next";
import { toast } from "react-hot-toast";
import { Virtuoso, type VirtuosoHandle } from "react-virtuoso";

import type { Profile } from "../../../../types/profile";
import {
  getLogFileContent,
  getProfileLogFiles,
  openLogFileDirectory,
  uploadLogToMclogs,
  parseLogLinesFromString,
  type LogLevel,
  type ParsedLogLine,
} from "../../../../services/log-service";
import { useDelayedTrue } from "../../../../hooks/useDelayedTrue";
import { ThemedDropdown, ThemedDropdownItem } from "../shared/ThemedDropdown";
import { parseErrorMessage } from "../../../../utils/error-utils";
import {
  LevelChips,
  LogEmptyState,
  LogRowView,
  LogSearchField,
  LogSummary,
  LogToolButton,
  countLevels,
  type LevelKey,
} from "../../../log/salami-log-ui";

interface LogsTabV3Props {
  profile: Profile;
  isActive?: boolean;
  onRefresh?: () => void;
}

const DEFAULT_LEVELS: Record<LogLevel, boolean> = {
  ERROR: true, WARN: true, INFO: true, DEBUG: true, TRACE: false,
};

function getFilename(path: string): string {
  return path.split(/[\\/]/).pop() || path;
}

export function LogsTabV3({ profile, isActive = true, onRefresh }: LogsTabV3Props) {
  const { t } = useTranslation();

  // ── Daten ─────────────────────────────────────────────────────────────────
  const [logFiles, setLogFiles] = useState<string[]>([]);
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [rawContent, setRawContent] = useState<string>("");
  const [isLoadingList, setIsLoadingList] = useState(false);
  const [isLoadingContent, setIsLoadingContent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ── Ansicht ───────────────────────────────────────────────────────────────
  const [searchQuery, setSearchQuery] = useState("");
  const [enabledLevels, setEnabledLevels] = useState<Record<LogLevel, boolean>>(DEFAULT_LEVELS);
  const [fileMenuOpen, setFileMenuOpen] = useState(false);
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [showThreadPrefix, setShowThreadPrefix] = useState(false);
  const [wordWrap, setWordWrap] = useState(true);
  const [atBottom, setAtBottom] = useState(true);
  const [isUploading, setIsUploading] = useState(false);

  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  // Dateiliste: latest.log oben, dann neueste zuerst. Bei Profilwechsel fällt die Auswahl auf
  // die erste Datei zurück, wenn die alte nicht mehr existiert.
  const loadFiles = useCallback(async () => {
    if (!profile?.id) return;
    setIsLoadingList(true);
    setError(null);
    try {
      const paths = await getProfileLogFiles(profile.id);
      if (!mountedRef.current) return;
      paths.sort((a, b) => {
        const aName = getFilename(a).toLowerCase();
        const bName = getFilename(b).toLowerCase();
        if (aName === "latest.log") return -1;
        if (bName === "latest.log") return 1;
        return bName.localeCompare(aName);
      });
      setLogFiles(paths);
      setSelectedPath(prev => (prev && paths.includes(prev)) ? prev : (paths[0] ?? null));
      onRefresh?.();
    } catch (err) {
      console.error("[V3 Logs] Failed to list:", err);
      if (mountedRef.current) setError(parseErrorMessage(err));
    } finally {
      if (mountedRef.current) setIsLoadingList(false);
    }
  }, [profile.id, onRefresh]);

  useEffect(() => {
    if (isActive) void loadFiles();
  }, [isActive, loadFiles]);

  useEffect(() => {
    if (!selectedPath) {
      setRawContent("");
      return;
    }
    setIsLoadingContent(true);
    setError(null);
    (async () => {
      try {
        const content = await getLogFileContent(selectedPath);
        if (mountedRef.current) setRawContent(content);
      } catch (err) {
        console.error("[V3 Logs] Failed to load content:", err);
        if (mountedRef.current) setError(parseErrorMessage(err));
      } finally {
        if (mountedRef.current) setIsLoadingContent(false);
      }
    })();
  }, [selectedPath]);

  const parsedLines = useMemo<ParsedLogLine[]>(() => {
    if (!rawContent) return [];
    return parseLogLinesFromString(rawContent);
  }, [rawContent]);

  // nur Meldungen zählen, nicht ihre Folgezeilen (Stacktraces erben die Stufe)
  const counts = useMemo(() => countLevels(parsedLines.filter((l) => l.timestamp).map((l) => l.level)), [parsedLines]);

  // Filter: Folgezeilen (z. B. Stacktraces) erben die Stufe der Zeile davor.
  const visibleLines = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const result: ParsedLogLine[] = [];
    let inheritedLevel: LogLevel | null = null;
    for (const line of parsedLines) {
      const effective = line.level ?? inheritedLevel;
      if (line.level) inheritedLevel = line.level;
      if (effective && !enabledLevels[effective]) continue;
      if (!effective && !Object.values(enabledLevels).some(Boolean)) continue;
      if (q) {
        const hay = `${line.text} ${line.thread ?? ""} ${line.level ?? ""}`.toLowerCase();
        if (!hay.includes(q)) continue;
      }
      result.push(line);
    }
    return result;
  }, [parsedLines, searchQuery, enabledLevels]);

  const shouldShowLoading = useDelayedTrue(isLoadingContent && parsedLines.length === 0, 500);

  const toggleLevel = (lvl: LevelKey) => setEnabledLevels(prev => ({ ...prev, [lvl]: !prev[lvl] }));

  const handleOpenFolder = useCallback(async () => {
    if (!selectedPath) return;
    try {
      await openLogFileDirectory(selectedPath);
    } catch (err) {
      toast.error(parseErrorMessage(err));
    }
  }, [selectedPath]);

  const handleUpload = useCallback(async () => {
    if (!rawContent || isUploading) return;
    setIsUploading(true);
    try {
      const url = await toast.promise(
        uploadLogToMclogs(rawContent),
        {
          loading: t("profiles.v3.logs.uploading"),
          success: (u: string) => t("profiles.v3.logs.uploadSuccess", { url: u }),
          error: (err) => t("profiles.v3.logs.uploadFailed", { error: parseErrorMessage(err) }),
        },
      );
      try {
        await navigator.clipboard.writeText(url);
        toast.success(t("profiles.v3.logs.urlCopied"));
      } catch { /* Zwischenablage gesperrt – egal */ }
    } finally {
      if (mountedRef.current) setIsUploading(false);
    }
  }, [rawContent, isUploading, t]);

  const scrollToBottom = useCallback(() => {
    if (virtuosoRef.current && visibleLines.length > 0) {
      virtuosoRef.current.scrollToIndex({ index: visibleLines.length - 1, behavior: "auto" });
    }
  }, [visibleLines.length]);

  const selectedFilename = selectedPath ? getFilename(selectedPath) : t("profiles.v3.logs.noFile");

  return (
    <div className="relative flex min-h-0 flex-1 flex-col gap-3 p-4">
      {/* ── Kopf: Datei, Suche, Aktionen ──────────────────────────────────── */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative">
          <button
            onClick={() => setFileMenuOpen(v => !v)}
            disabled={logFiles.length === 0}
            className="flex h-9 max-w-[240px] items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.04] px-3 text-sm text-white/85 hover:bg-white/[0.08] disabled:opacity-50"
          >
            <Icon icon="solar:document-text-linear" className="h-4 w-4 shrink-0 text-white/50" />
            <span className="truncate">{selectedFilename}</span>
            <Icon icon="solar:alt-arrow-down-linear" className="h-3.5 w-3.5 shrink-0 text-white/40" />
          </button>
          <ThemedDropdown open={fileMenuOpen} onClose={() => setFileMenuOpen(false)} width="w-64" scrollable>
            {logFiles.map((path) => {
              const name = getFilename(path);
              return (
                <ThemedDropdownItem
                  key={path}
                  icon={name === "latest.log" ? "solar:pin-bold" : "solar:document-text-linear"}
                  selected={selectedPath === path}
                  onClick={() => { setSelectedPath(path); setFileMenuOpen(false); }}
                >
                  {name}
                </ThemedDropdownItem>
              );
            })}
          </ThemedDropdown>
        </div>

        <LogSearchField
          value={searchQuery}
          onChange={setSearchQuery}
          matches={searchQuery ? visibleLines.length : undefined}
          className="min-w-[200px] flex-1"
        />

        <LogToolButton
          icon="solar:share-bold"
          label={t("salami_logs.share")}
          title={t("salami_logs.share_tooltip")}
          onClick={handleUpload}
          disabled={!rawContent}
          busy={isUploading}
        />
        <LogToolButton
          icon="solar:folder-open-linear"
          title={t("salami_logs.open_folder")}
          onClick={handleOpenFolder}
          disabled={!selectedPath}
        />
        <div className="relative">
          <LogToolButton
            icon="solar:tuning-2-linear"
            title={t("salami_logs.options")}
            onClick={() => setOptionsOpen(v => !v)}
            active={optionsOpen}
          />
          <ThemedDropdown open={optionsOpen} onClose={() => setOptionsOpen(false)} width="w-56">
            <OptionToggle label={t("salami_logs.show_thread")} checked={showThreadPrefix} onChange={() => setShowThreadPrefix(v => !v)} />
            <OptionToggle label={t("salami_logs.wrap")} checked={wordWrap} onChange={() => setWordWrap(v => !v)} />
          </ThemedDropdown>
        </div>
      </div>

      <LevelChips enabled={enabledLevels} counts={counts} onToggle={toggleLevel} />

      {/* ── Inhalt ──────────────────────────────────────────────────────────── */}
      <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-black/25 py-2">
        {error && (
          <div className="absolute left-3 right-3 top-3 z-20 flex items-start gap-3 rounded-xl border border-red-400/30 bg-red-500/10 p-3">
            <Icon icon="solar:danger-triangle-bold" className="mt-0.5 h-5 w-5 shrink-0 text-red-300" />
            <div className="min-w-0 flex-1 break-words text-xs text-red-100">{error}</div>
            <button
              onClick={loadFiles}
              className="h-7 shrink-0 rounded-lg px-2 text-xs text-red-100 transition-colors hover:bg-red-500/20"
            >
              {t("profiles.v3.content.retry")}
            </button>
          </div>
        )}

        {isLoadingList && logFiles.length === 0 && !error ? (
          <div className="flex h-full items-center justify-center text-sm text-white/40">
            <Icon icon="svg-spinners:ring-resize" className="mr-2 h-4 w-4" />
            {t("profiles.v3.content.loading")}
          </div>
        ) : logFiles.length === 0 && !isLoadingList ? (
          <LogEmptyState title={t("profiles.v3.logs.emptyFiles")} hint={t("profiles.v3.logs.emptyFilesHint")} />
        ) : isLoadingContent && parsedLines.length === 0 ? (
          shouldShowLoading ? (
            <div className="flex h-full items-center justify-center text-sm text-white/40">
              <Icon icon="svg-spinners:ring-resize" className="mr-2 h-4 w-4" />
              {t("profiles.v3.content.loading")}
            </div>
          ) : null
        ) : visibleLines.length === 0 ? (
          <LogEmptyState
            icon="solar:magnifer-linear"
            title={searchQuery
              ? t("profiles.v3.logs.noMatchSearch", { query: searchQuery })
              : t("profiles.v3.logs.noMatchFilter")}
            hint={searchQuery ? undefined : t("profiles.v3.logs.noMatchFilterHint")}
          />
        ) : (
          <Virtuoso
            // neu aufbauen bei Dateiwechsel, damit die Position zurückgesetzt wird
            key={selectedPath ?? "none"}
            ref={virtuosoRef}
            data={visibleLines}
            atBottomStateChange={setAtBottom}
            className="select-text px-2"
            style={{ flex: 1, minHeight: 0 }}
            itemContent={(_idx, line) => (
              <LogRowView
                level={line.level}
                timestamp={line.timestamp}
                thread={line.thread}
                message={line.text}
                showThread={showThreadPrefix}
                wrap={wordWrap}
                query={searchQuery}
              />
            )}
            followOutput="auto"
            increaseViewportBy={400}
          />
        )}

        {!atBottom && visibleLines.length > 10 && (
          <div className="absolute bottom-3 right-3">
            <LogToolButton icon="solar:arrow-down-linear" label={t("salami_logs.to_bottom")} onClick={scrollToBottom} active />
          </div>
        )}
      </div>

      <LogSummary lines={parsedLines.length} counts={counts} />
    </div>
  );
}

const OptionToggle: React.FC<{ label: string; checked: boolean; onChange: () => void }> = ({ label, checked, onChange }) => (
  <button
    onClick={onChange}
    className="flex w-full items-center justify-between gap-3 px-3 py-2 text-sm text-white/80 transition-colors hover:bg-white/5 hover:text-white"
  >
    <span>{label}</span>
    <Icon
      icon={checked ? "solar:check-square-bold" : "solar:stop-linear"}
      className={checked ? "h-5 w-5 text-white" : "h-5 w-5 text-white/30"}
    />
  </button>
);
