/**
 * Log-Vorschau: das Log-Fenster mit Beispielzeilen im normalen Browser (`yarn dev`, dann
 * /design-preview-logs.html). Nur zum Gestalten – nicht im Build.
 */
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";

mockWindows("minecraft_log_window");
mockIPC(() => null);

const [{ default: React }, { default: ReactDOM }, { I18nextProvider }, { default: i18n }, { LogViewerCore }, { LogWindowTitlebar }] =
  await Promise.all([
    import("react"),
    import("react-dom/client"),
    import("react-i18next"),
    import("../i18n/i18n"),
    import("../components/log/LogViewerCore"),
    import("../components/log/LogWindowTitlebar"),
  ]);
await import("../styles/globals.css");

type Level = "ERROR" | "WARN" | "INFO" | "DEBUG" | "TRACE" | "UNKNOWN";
const raw: [string | null, Level, string | null, string][] = [
  ["21:27:03", "INFO", "main", "Loading Minecraft 1.21.11 with Fabric Loader 0.19.5"],
  ["21:27:05", "INFO", "main", "Loading 52 mods:"],
  [null, "INFO", null, "\t- salami 0.1.0"],
  ["21:27:06", "WARN", "main", "Mod 'Reese's Sodium Options' recommends sodium-extra 0.4.2 or later, which is missing!"],
  ["21:27:09", "INFO", "Render thread", "[Salami] Salami Client 0.1.0 geladen – 42 Module"],
  ["21:27:10", "DEBUG", "Render thread", "Reloading ResourceManager: vanilla, fabric, salami"],
  ["21:27:12", "WARN", "Render thread", "Missing sound for event: minecraft:item.goat_horn.play"],
  ["21:27:20", "INFO", "Server thread", "Starting integrated minecraft server version 1.21.11"],
  ["21:27:24", "ERROR", "Render thread", "Failed to load texture salami:textures/cape/custom.png"],
  [null, "ERROR", null, "java.io.FileNotFoundException: salami:textures/cape/custom.png"],
  [null, "ERROR", null, "\tat dev.salamiclient.modules.visual.CapesModule.loadCustom(CapesModule.java:131)"],
  ["21:27:26", "INFO", "Render thread", "[CHAT] SalamiFan joined the game"],
];
const logs = raw.map(([time, level, thread, message], i) => ({
  id: `l${i}`,
  processId: "p1",
  timestamp: time ? new Date(`2026-10-14T${time}`) : null,
  level,
  thread,
  message,
  raw: message,
}));

function Preview() {
  return (
    <div
      className="h-screen flex flex-col text-white"
      style={{ background: "radial-gradient(120% 60% at 50% -10%, #b23a3a26 0%, transparent 60%), #0f0b0b" }}
    >
      <LogWindowTitlebar title="PVP · SalamiFan" />
      <div className="flex-1 flex min-h-0 p-3">
        <LogViewerCore
          logs={logs}
          onClear={() => {}}
          onOpenFolder={() => {}}
          logFiles={["C:/Salami/PVP/logs/latest.log", "C:/Salami/PVP/logs/debug.log"]}
          selectedLogPath="C:/Salami/PVP/logs/latest.log"
          onLogSelect={() => {}}
        />
      </div>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <I18nextProvider i18n={i18n}>
      <Preview />
    </I18nextProvider>
  </React.StrictMode>,
);
