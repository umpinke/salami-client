import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { LogEmptyState } from "./salami-log-ui";
import { useTranslation } from "react-i18next";
import { useThemeStore } from "../../store/useThemeStore";
import { useFontStore } from "../../store/font-store";
import { useMinecraftAuthStore } from "../../store/minecraft-auth-store";
import { LogWindowTitlebar } from "./LogWindowTitlebar";
import { InstanceSidebar } from "./InstanceSidebar";
import { LogViewerCore } from "./LogViewerCore";
import { useProcessEvents, useProcessLogs } from "../../hooks/useProcessEvents";
import { useProcessLogCursor } from "../../hooks/useProcessLogCursor";
import { useProcessStore } from "../../store/useProcessStore";
import type { ProcessMetadata } from "../../types/processState";

interface MinecraftLogWindowProps {
  crashedProcess?: ProcessMetadata;
}

export function MinecraftLogWindow({ crashedProcess }: MinecraftLogWindowProps) {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);

  const { processes } = useProcessEvents({ autoFetch: true });
  const { logs: rawLogs } = useProcessLogs(selectedInstanceId);

  const {
    stoppedProcesses,
    launcherLogs: launcherLogsMap,
    selectedProcessId,
    selectProcess,
    clearLogs,
    clearLauncherLogs,
    markProcessStopped,
  } = useProcessStore();

  const crashedProcessHandledRef = useRef(false);
  useEffect(() => {
    if (crashedProcess && !crashedProcessHandledRef.current) {
      crashedProcessHandledRef.current = true;
      markProcessStopped(crashedProcess.id, crashedProcess);
      setSelectedInstanceId(crashedProcess.id);
    }
  }, [crashedProcess, markProcessStopped]);

  useEffect(() => {
    const themeStore = useThemeStore.getState();
    themeStore.applyAccentColorToDOM();
    themeStore.applyBorderRadiusToDOM();
    useFontStore.getState().applyFontToDOM();
    useMinecraftAuthStore.getState().initializeAccounts();
  }, []);

  useEffect(() => {
    if (selectedProcessId && selectedProcessId !== selectedInstanceId) {
      setSelectedInstanceId(selectedProcessId);
    }
  }, [selectedProcessId, selectedInstanceId]);

  useEffect(() => {
    if (!selectedInstanceId && processes.length > 0) {
      const runningProcess = processes.find(p => p.state === "Running");
      if (runningProcess) {
        setSelectedInstanceId(runningProcess.id);
      } else {
        setSelectedInstanceId(processes[0].id);
      }
    }
  }, [processes, selectedInstanceId]);

  const { selectedProfileId, selectedSessionId } = useMemo(() => {
    if (!selectedInstanceId) return { selectedProfileId: null, selectedSessionId: null };
    const runningProcess = processes.find(p => p.id === selectedInstanceId);
    if (runningProcess) {
      return {
        selectedProfileId: runningProcess.profile_id,
        selectedSessionId: runningProcess.log_session_id ?? null,
      };
    }
    const stoppedProcess = stoppedProcesses.get(selectedInstanceId);
    if (stoppedProcess) {
      return {
        selectedProfileId: stoppedProcess.profile_id,
        selectedSessionId: stoppedProcess.log_session_id ?? null,
      };
    }
    return { selectedProfileId: null, selectedSessionId: null };
  }, [selectedInstanceId, processes, stoppedProcesses]);

  useProcessLogCursor(selectedSessionId, selectedInstanceId);

  const launcherLogs = useMemo(() => {
    if (!selectedProfileId) return [];
    return launcherLogsMap.get(selectedProfileId) || [];
  }, [selectedProfileId, launcherLogsMap]);

  const displayLogs = useMemo(() => {
    if (rawLogs.length > 0) {
      return rawLogs;
    }
    return launcherLogs;
  }, [rawLogs, launcherLogs]);

  const handleClear = () => {
    if (!selectedInstanceId) return;
    clearLogs(selectedInstanceId);
    if (selectedProfileId) {
      clearLauncherLogs(selectedProfileId);
    }
  };

  const handleSelectInstance = useCallback((id: string) => {
    setSelectedInstanceId(id);
    selectProcess(id);
  }, [selectProcess]);

  return (
    <div
      className="h-screen flex flex-col"
      style={{
        background: `radial-gradient(120% 60% at 50% -10%, ${accentColor.value}26 0%, transparent 60%), #0f0b0b`,
      }}
    >
      <LogWindowTitlebar />

      <div className="flex-1 flex min-h-0 p-3 gap-3">
        <div className="flex-[7] flex flex-col min-w-0">
          {!selectedInstanceId ? (
            <div className="flex-1 rounded-2xl border border-white/[0.06] bg-black/30">
              <LogEmptyState title={t('logs.select_instance')} hint={t('logs.select_instance_hint')} />
            </div>
          ) : (
            <LogViewerCore
              logs={displayLogs}
              onClear={handleClear}
              noLogsTitle={t('logs.no_logs_yet')}
              noLogsSubtitle={t('logs.waiting_for_output')}
            />
          )}
        </div>

        <div className="flex-[3] min-w-[280px] max-w-[350px]">
          <InstanceSidebar
            selectedInstanceId={selectedInstanceId || undefined}
            onSelectInstance={handleSelectInstance}
          />
        </div>
      </div>
    </div>
  );
}
