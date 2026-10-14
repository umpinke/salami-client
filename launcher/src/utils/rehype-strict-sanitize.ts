/**
 * Strenger Filter für Markdown mit eingebettetem HTML (Modrinth-Beschreibungen), läuft nach
 * `rehype-raw`. Alles hier kommt von Fremden: Wer ein Projekt veröffentlicht, bestimmt den Text.
 *
 * - Entfernt Elemente, mit denen eine Beschreibung das Fenster wegnavigieren, Seiten-CSS
 *   überschreiben oder Fremdinhalte einbetten könnte (Formulare, `meta`/`base`/`link`/`style`,
 *   `object`/`embed`, Bild-Maps, Skripte).
 * - Entfernt Inline-Styles (unsichtbare Vollbild-Overlays), Event-Handler und Formular-Ziele.
 * - Erlaubt `iframe` nur für YouTube/Vimeo-Einbettungen und ohne `srcdoc`.
 */

interface HastNode {
  type: string;
  tagName?: string;
  properties?: Record<string, unknown>;
  children?: HastNode[];
}

/** Komplett entfernen (samt Inhalt). */
const DROP = new Set([
  "script", "style", "link", "meta", "base", "object", "embed", "applet", "frame", "frameset",
  "noscript", "template", "svg", "math", "portal",
]);
/** Element entfernen, Inhalt (Text) behalten. */
const UNWRAP = new Set(["form", "button", "input", "select", "option", "textarea", "label", "fieldset", "map", "area", "dialog"]);
const EMBED_HOSTS = ["www.youtube.com", "www.youtube-nocookie.com", "player.vimeo.com"];

function isSafeEmbed(src: unknown): boolean {
  if (typeof src !== "string") return false;
  try {
    const url = new URL(src);
    return url.protocol === "https:" && EMBED_HOSTS.includes(url.hostname);
  } catch {
    return false;
  }
}

function cleanProperties(props: Record<string, unknown>) {
  for (const key of Object.keys(props)) {
    const k = key.toLowerCase();
    if (
      k === "style" || k.startsWith("on") || k === "action" || k === "formaction" ||
      k === "srcdoc" || k === "ping" || k === "form" || k === "http-equiv" || k === "httpequiv"
    ) {
      delete props[key];
    }
  }
}

function clean(node: HastNode) {
  if (!node.children) return;
  const next: HastNode[] = [];
  for (const child of node.children) {
    if (child.type === "raw" || child.type === "comment") continue;
    if (child.type === "element" && child.tagName) {
      const tag = child.tagName.toLowerCase();
      if (DROP.has(tag)) continue;
      if (tag === "iframe" && !isSafeEmbed(child.properties?.src)) continue;
      if (UNWRAP.has(tag)) {
        clean(child);
        next.push(...(child.children ?? []));
        continue;
      }
      if (child.properties) cleanProperties(child.properties);
      clean(child);
    }
    next.push(child);
  }
  node.children = next;
}

/** rehype-Plugin: `rehypePlugins={[rehypeRaw, rehypeStrictSanitize]}`. */
export function rehypeStrictSanitize() {
  return (tree: HastNode) => {
    clean(tree);
  };
}
