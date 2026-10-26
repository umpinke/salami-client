import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { listen } from "@tauri-apps/api/event";
import { Modal } from "../ui/Modal";
import { Button } from "../ui/buttons/Button";
import { openExternalUrl } from "../../services/tauri-service";
import { revealItemInDir } from "../../utils/opener-utils";
import { EventType, type EventPayload } from "../../types/events";
import "./curseforge-manual-i18n";

/** Eine Datei, die der Autor nur über die CurseForge-Webseite herausgibt (`integrations/curseforge_manual.rs`). */
interface ManualDownload {
  name: string;
  file_name: string;
  url: string;
  /** `null` = Mod, sonst Unterordner wie `resourcepacks`. */
  folder: string | null;
  folder_path: string | null;
}

/** Fenster mit den CurseForge-Dateien, die der Spieler selbst herunterladen muss. */
export function CurseForgeManualDownloadsModal() {
  const { t } = useTranslation();
  const [files, setFiles] = useState<ManualDownload[] | null>(null);

  useEffect(() => {
    const unlisten = listen<EventPayload>("state_event", (event) => {
      if (event.payload.event_type !== EventType.CurseforgeManualDownloads) return;
      try {
        const payload = JSON.parse(event.payload.message) as { files: ManualDownload[] };
        if (payload.files.length > 0) setFiles(payload.files);
      } catch (error) {
        console.error("[CurseForgeManualDownloads] Invalid payload:", error);
      }
    });
    return () => {
      void unlisten.then((stop) => stop());
    };
  }, []);

  if (!files) return null;

  const open = (url: string) =>
    openExternalUrl(url).catch((error) => toast.error(t("curseforge_manual.open_failed", { error: String(error) })));
  const reveal = (path: string) =>
    revealItemInDir(path).catch((error) => toast.error(t("curseforge_manual.open_failed", { error: String(error) })));

  const mods = files.filter((f) => !f.folder);
  const content = files.filter((f) => f.folder);

  const row = (file: ManualDownload) => (
    <li
      key={`${file.url}-${file.file_name}`}
      className="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-black/25 px-3 py-2"
    >
      <div className="min-w-0">
        <div className="truncate text-sm font-semibold text-white">{file.name}</div>
        <div className="truncate text-xs text-white/50">{file.file_name}</div>
        {file.folder && <div className="text-xs text-white/60">{t("curseforge_manual.folder", { folder: file.folder })}</div>}
      </div>
      <div className="flex shrink-0 gap-2">
        {file.folder_path && (
          <Button
            size="sm"
            variant="secondary"
            icon={<Icon icon="solar:folder-open-bold" className="h-4 w-4" />}
            onClick={() => void reveal(file.folder_path!)}
          >
            {t("curseforge_manual.open_folder")}
          </Button>
        )}
        <Button
          size="sm"
          icon={<Icon icon="solar:download-minimalistic-bold" className="h-4 w-4" />}
          onClick={() => void open(file.url)}
        >
          {t("curseforge_manual.open")}
        </Button>
      </div>
    </li>
  );

  return (
    <Modal
      title={t("curseforge_manual.title")}
      titleIcon={<Icon icon="solar:download-square-bold" className="h-7 w-7 text-amber-300" />}
      onClose={() => setFiles(null)}
      width="lg"
      footer={
        <div className="flex w-full justify-end gap-3">
          <Button variant="secondary" onClick={() => files.forEach((f) => void open(f.url))}>
            {t("curseforge_manual.open_all")}
          </Button>
          <Button onClick={() => setFiles(null)}>{t("curseforge_manual.close")}</Button>
        </div>
      }
    >
      <div className="space-y-5 p-6 text-white">
        <p className="text-sm leading-relaxed text-white/80">{t("curseforge_manual.intro")}</p>
        {mods.length > 0 && (
          <section className="space-y-2">
            <h3 className="text-base font-semibold">{t("curseforge_manual.mods")}</h3>
            <p className="text-xs leading-relaxed text-white/60">{t("curseforge_manual.mods_hint")}</p>
            <ul className="space-y-2">{mods.map(row)}</ul>
          </section>
        )}
        {content.length > 0 && (
          <section className="space-y-2">
            <h3 className="text-base font-semibold">{t("curseforge_manual.content")}</h3>
            <p className="text-xs leading-relaxed text-white/60">{t("curseforge_manual.content_hint")}</p>
            <ul className="space-y-2">{content.map(row)}</ul>
          </section>
        )}
      </div>
    </Modal>
  );
}
