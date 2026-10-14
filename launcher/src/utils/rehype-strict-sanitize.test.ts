import { test } from "node:test";
import assert from "node:assert/strict";
import { rehypeStrictSanitize } from "./rehype-strict-sanitize.ts";

type Node = { type: string; tagName?: string; value?: string; properties?: Record<string, unknown>; children?: Node[] };

const el = (tagName: string, properties: Record<string, unknown> = {}, children: Node[] = []): Node => ({
  type: "element",
  tagName,
  properties,
  children,
});
const text = (value: string): Node => ({ type: "text", value });

function run(children: Node[]): Node {
  const tree: Node = { type: "root", children };
  rehypeStrictSanitize()(tree as never);
  return tree;
}

function tags(node: Node): string[] {
  return (node.children ?? []).flatMap((c) => (c.type === "element" ? [c.tagName!, ...tags(c)] : []));
}

test("entfernt Navigation, Styles und Einbettungen", () => {
  const tree = run([
    el("form", { action: "https://evil.example" }, [el("button", { formAction: "https://evil.example" }, [text("Klick")])]),
    el("meta", { httpEquiv: "refresh", content: "0;url=https://evil.example" }),
    el("base", { href: "https://evil.example" }),
    el("style", {}, [text("body{display:none}")]),
    el("link", { rel: "stylesheet", href: "https://evil.example/x.css" }),
    el("object", { data: "https://evil.example" }),
    el("map", {}, [el("area", { href: "https://evil.example" })]),
    el("iframe", { srcDoc: "<p>x</p>" }),
    el("iframe", { src: "https://evil.example/frame" }),
  ]);
  assert.deepEqual(tags(tree), []);
  // Text aus Formular-Knöpfen bleibt lesbar
  assert.equal(JSON.stringify(tree).includes("Klick"), true);
});

test("entfernt gefährliche Attribute, behält normalen Inhalt", () => {
  const tree = run([
    el("div", { style: "position:fixed;inset:0", onClick: "x()", className: ["box"] }, [
      el("a", { href: "https://modrinth.com", ping: "https://evil.example" }, [text("Link")]),
      el("img", { src: "https://cdn.modrinth.com/a.png", alt: "Bild" }),
    ]),
    el("iframe", { src: "https://www.youtube.com/embed/abc" }),
  ]);
  const div = tree.children![0];
  assert.equal(div.properties!.style, undefined);
  assert.equal(div.properties!.onClick, undefined);
  assert.deepEqual(div.properties!.className, ["box"]);
  const link = div.children![0];
  assert.equal(link.properties!.href, "https://modrinth.com");
  assert.equal(link.properties!.ping, undefined);
  assert.deepEqual(tags(tree), ["div", "a", "img", "iframe"]);
});
