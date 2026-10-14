import { useState, useEffect, useRef, useCallback, useMemo, useDeferredValue } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { useThemeStore } from "../../store/useThemeStore";
import { useLogSettingsStore } from "../../store/useLogSettingsStore";
import { LogEntry, LogLevel } from "../../store/useProcessStore";
import { openExternalUrl } from "../../services/tauri-service";
import { uploadLogToMclogs } from "../../services/log-service";
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import { Virtuoso, type VirtuosoHandle } from "react-virtuoso";
import { parseErrorMessage } from "../../utils/error-utils";
import type React from "react";
import {
  LevelChips,
  LogEmptyState,
  LogRowView,
  LogSearchField,
  LogSummary,
  LogToolButton,
  countLevels,
} from "./salami-log-ui";

interface DisplayLogLine {
  id: string;
  timestamp: string | null;
  level: LogLevel;
  thread: string | null;
  message: string;
  processId: string;
}

// Convert LogEntry to DisplayLogLine
function logEntryToDisplayLine(entry: LogEntry): DisplayLogLine {
  const timestamp = entry.timestamp
    ? entry.timestamp.toLocaleTimeString("de-DE", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : null;

  return {
    id: entry.id,
    timestamp,
    level: entry.level,
    thread: entry.thread,
    message: entry.message,
    processId: entry.processId,
  };
}

function formatLogLineForCopy(log: DisplayLogLine, showThreadPrefix: boolean): string {
  if (log.timestamp) {
    if (showThreadPrefix) {
      return `[${log.timestamp}] [${log.thread || "main"}/${log.level}] ${log.message}`;
    }
    return `[${log.timestamp}] ${log.message}`;
  }
  return `    ${log.message}`;
}

function getLogIndexFromNode(node: Node | null): number | null {
  if (!node) return null;
  const element = node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement;
  const row = element?.closest("[data-log-index]");
  if (!row) return null;
  const index = Number.parseInt(row.getAttribute("data-log-index") ?? "", 10);
  return Number.isNaN(index) ? null : index;
}

function getLogIndexFromPoint(clientX: number, clientY: number): number | null {
  return getLogIndexFromNode(document.elementFromPoint(clientX, clientY));
}

function buildLogCopyText(
  logs: DisplayLogLine[],
  range: { start: number; end: number },
  showThreadPrefix: boolean,
): string {
  return logs
    .slice(range.start, range.end + 1)
    .map((log) => formatLogLineForCopy(log, showThreadPrefix))
    .join("\n");
}

export interface LogViewerCoreProps {
  logs: LogEntry[];
  onClear?: () => void;
  showNoLogsMessage?: boolean;
  noLogsIcon?: string;
  noLogsTitle?: string;
  noLogsSubtitle?: string;
  logFiles?: string[];
  selectedLogPath?: string | null;
  onLogSelect?: (path: string) => void;
  onOpenFolder?: () => void;
}

export function LogViewerCore({
  logs,
  onClear,
  showNoLogsMessage = true,
  noLogsIcon,
  noLogsTitle,
  noLogsSubtitle,
  logFiles,
  selectedLogPath,
  onLogSelect,
  onOpenFolder,
}: LogViewerCoreProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);
  const { showThreadPrefix, toggleShowThreadPrefix } = useLogSettingsStore();
  const [searchTerm, setSearchTerm] = useState("");
  const deferredSearchTerm = useDeferredValue(searchTerm);
  const [levelFilters, setLevelFilters] = useState<Record<LogLevel, boolean>>({
    ERROR: true,
    WARN: true,
    INFO: true,
    DEBUG: true,
    TRACE: false,
    UNKNOWN: true,
  });
  const [isAutoscrollEnabled, setIsAutoscrollEnabled] = useState(true);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const settingsButtonRef = useRef<HTMLElement>(null);
  const settingsPopupRef = useRef<HTMLDivElement>(null);
  const fileDropdownButtonRef = useRef<HTMLButtonElement>(null);
  const fileDropdownRef = useRef<HTMLDivElement>(null);
  const [isFileDropdownOpen, setIsFileDropdownOpen] = useState(false);
  const logContainerRef = useRef<HTMLDivElement>(null);
  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const [selectionRange, setSelectionRange] = useState<{ start: number; end: number } | null>(null);
  const [isPointerSelecting, setIsPointerSelecting] = useState(false);
  const selectionAnchorIndexRef = useRef<number | null>(null);
  const isMouseSelectingRef = useRef(false);
  const isAutoscrollEnabledRef = useRef(isAutoscrollEnabled);
  isAutoscrollEnabledRef.current = isAutoscrollEnabled;
  // State for delayed "NO LOGS YET" display
  const [showNoLogs, setShowNoLogs] = useState(false);

  // Convert log entries to display format
  const displayLogs = useMemo(() => {
    return logs.map(logEntryToDisplayLine);
  }, [logs]);

  // Filter logs based on search and level filters. Search uses React deferred input
  // so large log tails do not rescan synchronously on every keystroke.
  const filteredLogs = useMemo(() => {
    const searchLower = deferredSearchTerm.trim().toLowerCase();
    return displayLogs.filter((log) => {
      if (!levelFilters[log.level]) return false;
      if (!searchLower) return true;

      return (
        log.message.toLowerCase().includes(searchLower) ||
        log.thread?.toLowerCase().includes(searchLower) ||
        log.level.toLowerCase().includes(searchLower)
      );
    });
  }, [displayLogs, levelFilters, deferredSearchTerm]);

  const filteredLogsRef = useRef(filteredLogs);
  filteredLogsRef.current = filteredLogs;
  const selectionRangeRef = useRef(selectionRange);
  selectionRangeRef.current = selectionRange;

  useEffect(() => {
    if (isMouseSelectingRef.current) return;
    if (!isAutoscrollEnabled || filteredLogs.length === 0) return;
    const lastIndex = filteredLogs.length - 1;
    const jumpToBottom = () =>
      virtuosoRef.current?.scrollToIndex({
        index: lastIndex,
        align: "end",
        behavior: "auto",
      });
    jumpToBottom();
    const raf = requestAnimationFrame(jumpToBottom);
    return () => cancelAnimationFrame(raf);
  }, [filteredLogs.length, isAutoscrollEnabled]);

  const userScrollRef = useRef(false);
  const userScrollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const markUserScroll = useCallback(() => {
    userScrollRef.current = true;
    if (userScrollTimer.current) clearTimeout(userScrollTimer.current);
    userScrollTimer.current = setTimeout(() => {
      userScrollRef.current = false;
    }, 400);
  }, []);
  useEffect(() => () => {
    if (userScrollTimer.current) clearTimeout(userScrollTimer.current);
  }, []);

  const handleAtBottomChange = useCallback((atBottom: boolean) => {
    if (isMouseSelectingRef.current) return;
    if (atBottom) {
      setIsAutoscrollEnabled(true);
      return;
    }
    if (userScrollRef.current) setIsAutoscrollEnabled(false);
  }, []);

  // Delay showing "NO LOGS YET" by 1 second to avoid flicker
  useEffect(() => {
    setShowNoLogs(false);
    if (filteredLogs.length === 0) {
      const timer = setTimeout(() => setShowNoLogs(true), 1000);
      return () => clearTimeout(timer);
    }
  }, [filteredLogs.length]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        isSettingsOpen &&
        settingsPopupRef.current &&
        settingsButtonRef.current &&
        !settingsPopupRef.current.contains(event.target as Node) &&
        !settingsButtonRef.current.contains(event.target as Node)
      ) {
        setIsSettingsOpen(false);
      }
      if (
        isFileDropdownOpen &&
        fileDropdownRef.current &&
        fileDropdownButtonRef.current &&
        !fileDropdownRef.current.contains(event.target as Node) &&
        !fileDropdownButtonRef.current.contains(event.target as Node)
      ) {
        setIsFileDropdownOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isSettingsOpen, isFileDropdownOpen]);

  const commitSelectionIndices = useCallback((anchorIndex: number, focusIndex: number) => {
    setSelectionRange({
      start: Math.min(anchorIndex, focusIndex),
      end: Math.max(anchorIndex, focusIndex),
    });
  }, []);

  const copySelectedLogs = useCallback(() => {
    const range = selectionRangeRef.current;
    if (!range) return false;

    const text = buildLogCopyText(filteredLogsRef.current, range, showThreadPrefix);
    if (!text) return false;

    void writeText(text).catch((error) => {
      console.error("Failed to copy selected logs:", error);
      toast.error(t("logs.copy_failed", { defaultValue: "Failed to copy logs" }));
    });
    return true;
  }, [showThreadPrefix, t]);

  useEffect(() => {
    selectionAnchorIndexRef.current = null;
    setSelectionRange(null);
    window.getSelection()?.removeAllRanges();
  }, [searchTerm, levelFilters, selectedLogPath]);

  useEffect(() => {
    const container = logContainerRef.current;
    if (!container) return;

    const lastPointer = { x: 0, y: 0 };
    let edgeScrollRaf: number | null = null;

    const getScroller = () =>
      container.querySelector("[data-virtuoso-scroller]") as HTMLElement | null;

    const getEdgeScrollDelta = (clientY: number) => {
      const rect = container.getBoundingClientRect();
      const edgeThreshold = 56;
      const maxSpeed = 9;

      if (clientY >= rect.bottom - edgeThreshold) {
        const intensity = Math.min(1, (clientY - (rect.bottom - edgeThreshold)) / edgeThreshold);
        return maxSpeed * (0.2 + intensity * 0.8);
      }
      if (clientY <= rect.top + edgeThreshold) {
        const intensity = Math.min(1, (rect.top + edgeThreshold - clientY) / edgeThreshold);
        return -maxSpeed * (0.2 + intensity * 0.8);
      }
      return 0;
    };

    const getVisibleBoundaryIndex = (direction: "up" | "down"): number | null => {
      const rows = Array.from(container.querySelectorAll<HTMLElement>("[data-log-index]"));
      if (rows.length === 0) return null;

      const boundaryRow = direction === "up" ? rows[0] : rows[rows.length - 1];
      const index = Number.parseInt(boundaryRow.getAttribute("data-log-index") ?? "", 10);
      return Number.isNaN(index) ? null : index;
    };

    const updateSelectionFromPointer = (
      clientX: number,
      clientY: number,
      fallbackDirection?: "up" | "down",
    ) => {
      const anchorIndex = selectionAnchorIndexRef.current;
      if (anchorIndex === null) return;

      let hitIndex = getLogIndexFromPoint(clientX, clientY);
      if (hitIndex === null && fallbackDirection) {
        hitIndex = getVisibleBoundaryIndex(fallbackDirection);
      }

      if (hitIndex !== null) {
        commitSelectionIndices(anchorIndex, hitIndex);
      }
    };

    const stopEdgeScroll = () => {
      if (edgeScrollRaf !== null) {
        cancelAnimationFrame(edgeScrollRaf);
        edgeScrollRaf = null;
      }
    };

    const tickEdgeScroll = () => {
      if (!isMouseSelectingRef.current) {
        stopEdgeScroll();
        return;
      }

      const scrollDelta = getEdgeScrollDelta(lastPointer.y);
      if (scrollDelta !== 0) {
        const scroller = getScroller();
        if (scroller) {
          scroller.scrollTop += scrollDelta;
        }
        updateSelectionFromPointer(
          lastPointer.x,
          lastPointer.y,
          scrollDelta < 0 ? "up" : "down",
        );
      }

      if (getEdgeScrollDelta(lastPointer.y) !== 0) {
        edgeScrollRaf = requestAnimationFrame(tickEdgeScroll);
      } else {
        edgeScrollRaf = null;
      }
    };

    const syncEdgeScroll = (clientY: number) => {
      if (getEdgeScrollDelta(clientY) === 0) {
        stopEdgeScroll();
        return;
      }
      if (edgeScrollRaf === null) {
        edgeScrollRaf = requestAnimationFrame(tickEdgeScroll);
      }
    };

    const onPointerDown = (event: PointerEvent) => {
      if (event.button !== 0) return;

      const anchorIndex = getLogIndexFromNode(event.target as Node);
      if (anchorIndex === null) return;

      event.preventDefault();
      window.getSelection()?.removeAllRanges();
      container.focus({ preventScroll: true });

      isMouseSelectingRef.current = true;
      setIsPointerSelecting(true);
      selectionAnchorIndexRef.current = anchorIndex;
      commitSelectionIndices(anchorIndex, anchorIndex);

      container.setPointerCapture(event.pointerId);

      if (isAutoscrollEnabledRef.current) {
        setIsAutoscrollEnabled(false);
      }
    };

    const onPointerMove = (event: PointerEvent) => {
      if (!isMouseSelectingRef.current) return;
      lastPointer.x = event.clientX;
      lastPointer.y = event.clientY;
      updateSelectionFromPointer(event.clientX, event.clientY);
      syncEdgeScroll(event.clientY);
    };

    const onPointerUp = (event: PointerEvent) => {
      if (!isMouseSelectingRef.current) return;

      stopEdgeScroll();
      lastPointer.x = event.clientX;
      lastPointer.y = event.clientY;
      updateSelectionFromPointer(event.clientX, event.clientY);
      isMouseSelectingRef.current = false;
      setIsPointerSelecting(false);

      if (container.hasPointerCapture(event.pointerId)) {
        container.releasePointerCapture(event.pointerId);
      }
    };

    const onKeyDown = (event: KeyboardEvent) => {
      const mod = event.ctrlKey || event.metaKey;
      const key = event.key.toLowerCase();

      if (key === "escape") {
        selectionAnchorIndexRef.current = null;
        setSelectionRange(null);
        window.getSelection()?.removeAllRanges();
        return;
      }

      if (mod && key === "c") {
        if (!selectionRangeRef.current) return;
        event.preventDefault();
        copySelectedLogs();
      }
    };

    container.addEventListener("pointerdown", onPointerDown);
    container.addEventListener("pointermove", onPointerMove);
    container.addEventListener("pointerup", onPointerUp);
    container.addEventListener("pointercancel", onPointerUp);
    container.addEventListener("keydown", onKeyDown);

    return () => {
      stopEdgeScroll();
      container.removeEventListener("pointerdown", onPointerDown);
      container.removeEventListener("pointermove", onPointerMove);
      container.removeEventListener("pointerup", onPointerUp);
      container.removeEventListener("pointercancel", onPointerUp);
      container.removeEventListener("keydown", onKeyDown);
    };
  }, [commitSelectionIndices, copySelectedLogs]);

  const toggleLevelFilter = (level: LogLevel) => {
    setLevelFilters((prev) => ({ ...prev, [level]: !prev[level] }));
  };

  const [isUploading, setIsUploading] = useState(false);

  const handleUpload = async () => {
    if (filteredLogs.length === 0) {
      toast.error(t('logs.no_logs_to_upload'));
      return;
    }

    setIsUploading(true);
    try {
      const logText = filteredLogs
        .map((log) => formatLogLineForCopy(log, true))
        .join("\n");

      // Use Tauri backend command instead of direct fetch (CSP blocked in production)
      const url = await uploadLogToMclogs(logText);

      await writeText(url);
      toast.success(t('logs.uploaded_url_copied'));
      await openExternalUrl(url);
    } catch (error) {
      console.error("Failed to upload logs:", error);
      // Extract error message properly from Tauri CommandError
      const errorMessage = error && typeof error === 'object' && 'message' in error
        ? (error as { message: string }).message
        : parseErrorMessage(error);
      toast.error(errorMessage || t('logs.upload_failed'));
    } finally {
      setIsUploading(false);
    }
  };

  const scrollToBottom = () => {
    if (virtuosoRef.current && filteredLogs.length > 0) {
      virtuosoRef.current.scrollToIndex({ index: filteredLogs.length - 1, align: "end", behavior: "auto" });
    }
    setIsAutoscrollEnabled(true);
  };

  const renderLogLine = (index: number, log: DisplayLogLine) => {
    const isSelected =
      selectionRange !== null && index >= selectionRange.start && index <= selectionRange.end;
    return (
      <LogRowView
        level={log.level}
        timestamp={log.timestamp}
        thread={log.thread}
        message={log.message}
        showThread={showThreadPrefix}
        query={deferredSearchTerm}
        selected={isSelected}
        rowProps={{ "data-log-id": log.id, "data-log-index": index }}
      />
    );
  };

  const levelCounts = useMemo(
    () => countLevels(displayLogs.filter((log) => log.timestamp).map((log) => log.level)),
    [displayLogs],
  );

  return (
    <div className="flex-1 flex flex-col min-h-0 gap-3">
      {/* Kopf: Datei, Suche, Anzeige-Optionen */}
      <div className="flex flex-wrap items-center gap-2">
        {logFiles && logFiles.length > 0 && onLogSelect && (
          <button
            ref={fileDropdownButtonRef}
            onClick={() => setIsFileDropdownOpen(!isFileDropdownOpen)}
            className="flex h-9 max-w-[260px] items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.04] px-3 text-sm text-white/85 hover:bg-white/[0.08]"
          >
            <Icon icon="solar:document-text-linear" className="h-4 w-4 shrink-0 text-white/50" />
            <span className="truncate">
              {selectedLogPath ? (selectedLogPath.split(/[\\/]/).pop() || selectedLogPath) : t("salami_logs.choose_file")}
            </span>
            <Icon icon="solar:alt-arrow-down-linear" className="h-3.5 w-3.5 shrink-0 text-white/40" />
          </button>
        )}
        <LogSearchField
          value={searchTerm}
          onChange={setSearchTerm}
          matches={searchTerm ? filteredLogs.length : undefined}
          className="min-w-[200px] flex-1"
        />
        <span ref={settingsButtonRef as unknown as React.RefObject<HTMLSpanElement>}>
          <LogToolButton
            icon="solar:tuning-2-linear"
            title={t("salami_logs.options")}
            onClick={() => setIsSettingsOpen(!isSettingsOpen)}
            active={isSettingsOpen}
          />
        </span>
      </div>

      <LevelChips
        enabled={levelFilters}
        counts={levelCounts}
        onToggle={(level) => toggleLevelFilter(level as LogLevel)}
      />

      {/* Log-Inhalt */}
      <div
        ref={logContainerRef}
        tabIndex={0}
        role="region"
        aria-label={t("logs.viewer_region", { defaultValue: "Log output" })}
        onMouseDown={() => logContainerRef.current?.focus({ preventScroll: true })}
        onWheel={markUserScroll}
        onTouchMove={markUserScroll}
        onPointerDown={markUserScroll}
        className="flex-1 min-h-0 flex flex-col rounded-2xl border border-white/[0.06] bg-black/30 p-2 outline-none focus-visible:ring-1 focus-visible:ring-white/20"
      >
        {filteredLogs.length === 0 ? (
          showNoLogs && showNoLogsMessage && (
            <LogEmptyState
              icon={noLogsIcon}
              title={noLogsTitle ?? t("salami_logs.empty_title")}
              hint={noLogsSubtitle ?? t("salami_logs.empty_hint")}
            />
          )
        ) : (
          <Virtuoso
            ref={virtuosoRef}
            className={`custom-scrollbar overflow-x-hidden ${isPointerSelecting ? "select-none" : "select-text"}`}
            style={{ flex: 1, minHeight: 0 }}
            data={filteredLogs}
            computeItemKey={(_index, log) => log.id}
            increaseViewportBy={{ top: 400, bottom: 400 }}
            atBottomStateChange={handleAtBottomChange}
            initialTopMostItemIndex={filteredLogs.length > 0 ? filteredLogs.length - 1 : 0}
            itemContent={(index, log) => renderLogLine(index, log)}
          />
        )}
      </div>

      {/* Statuszeile */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <LogSummary lines={filteredLogs.length} counts={levelCounts} />
          <LogToolButton
            icon={isAutoscrollEnabled ? "solar:play-circle-linear" : "solar:pause-circle-linear"}
            label={isAutoscrollEnabled ? t("salami_logs.live") : t("salami_logs.paused")}
            onClick={() => (isAutoscrollEnabled ? setIsAutoscrollEnabled(false) : scrollToBottom())}
            active={isAutoscrollEnabled}
          />
          {!isAutoscrollEnabled && (
            <LogToolButton icon="solar:arrow-down-linear" label={t("salami_logs.to_bottom")} onClick={scrollToBottom} />
          )}
        </div>
        <div className="flex items-center gap-2">
          {onClear && <LogToolButton icon="solar:trash-bin-minimalistic-linear" label={t("salami_logs.clear")} onClick={onClear} />}
          {onOpenFolder && <LogToolButton icon="solar:folder-open-linear" label={t("salami_logs.open_folder")} onClick={onOpenFolder} />}
          <LogToolButton
            icon="solar:share-bold"
            label={t("salami_logs.share")}
            title={t("salami_logs.share_tooltip")}
            onClick={handleUpload}
            busy={isUploading}
          />
        </div>
      </div>

      {/* Anzeige-Optionen */}
      {isSettingsOpen && settingsButtonRef.current && createPortal(
        <div
          ref={settingsPopupRef}
          className="fixed min-w-[240px] rounded-xl border border-white/10 bg-[#141010]/95 p-1 shadow-2xl backdrop-blur"
          style={{
            top: settingsButtonRef.current.getBoundingClientRect().bottom + 8,
            right: window.innerWidth - settingsButtonRef.current.getBoundingClientRect().right,
            zIndex: 9999,
          }}
        >
          <button
            onClick={toggleShowThreadPrefix}
            className="flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-sm text-white/80 transition-colors hover:bg-white/5 hover:text-white"
          >
            <span>{t("salami_logs.show_thread")}</span>
            <Icon
              icon={showThreadPrefix ? "solar:check-square-bold" : "solar:stop-linear"}
              className={showThreadPrefix ? "h-5 w-5" : "h-5 w-5 text-white/30"}
              style={showThreadPrefix ? { color: accentColor.value } : undefined}
            />
          </button>
        </div>,
        document.body
      )}

      {isFileDropdownOpen && fileDropdownButtonRef.current && createPortal(
        <div
          ref={fileDropdownRef}
          className="fixed min-w-[220px] max-w-[400px] max-h-[300px] overflow-y-auto rounded-xl border border-white/10 bg-[#141010]/95 p-1 shadow-2xl backdrop-blur custom-scrollbar"
          style={{
            top: fileDropdownButtonRef.current.getBoundingClientRect().bottom + 8,
            left: fileDropdownButtonRef.current.getBoundingClientRect().left,
            zIndex: 9999,
          }}
        >
          {logFiles?.map((path) => {
            const name = path.split(/[\\/]/).pop() || path;
            const isSelected = path === selectedLogPath;
            return (
              <button
                key={path}
                onClick={() => { onLogSelect?.(path); setIsFileDropdownOpen(false); }}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-white/5"
                style={{ color: isSelected ? accentColor.value : "rgba(255,255,255,0.75)" }}
              >
                <Icon icon={name === "latest.log" ? "solar:pin-bold" : "solar:document-text-linear"} className="h-4 w-4 shrink-0" />
                <span className="truncate">{name}</span>
              </button>
            );
          })}
        </div>,
        document.body
      )}
    </div>
  );
}
