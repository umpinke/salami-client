import i18n from "../i18n/i18n";

export function formatFileSize(bytes?: number): string {
  if (!bytes) return i18n.t("common.unknown_size");
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}
