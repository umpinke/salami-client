import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

/** Event aus `src-tauri/src/utils/online_counter.rs`, Payload `number | null`. */
const ONLINE_COUNT_EVENT = "salami-online-count";

/**
 * Wie viele Salami-Launcher gerade offen sind (anonymer Online-Zähler).
 * `null`, solange die Zahl unbekannt ist: Zähler aus, Server nicht erreichbar oder kein Backend (Vorschau/Demo).
 */
export function useOnlineCount(): number | null {
  const [count, setCount] = useState<number | null>(null);

  useEffect(() => {
    let disposed = false;
    let gotEvent = false;
    let unlisten: (() => void) | undefined;

    void (async () => {
      try {
        const stop = await listen<number | null>(ONLINE_COUNT_EVENT, (event) => {
          gotEvent = true;
          setCount(event.payload ?? null);
        });
        if (disposed) {
          stop();
          return;
        }
        unlisten = stop;
        const initial = await invoke<number | null>("get_online_count");
        // Ein Event, das währenddessen kam, ist neuer als der Startwert.
        if (!disposed && !gotEvent) setCount(initial ?? null);
      } catch {
        // kein Backend (Website-Demo): keine Anzeige
      }
    })();

    return () => {
      disposed = true;
      unlisten?.();
    };
  }, []);

  return count;
}
