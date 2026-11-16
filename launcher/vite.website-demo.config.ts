/**
 * Build der Launcher-Demo für die Website (Einstieg: website-demo.html → src/dev/website-demo.ts).
 *
 *   node node_modules/vite/bin/vite.js build --config vite.website-demo.config.ts
 *
 * Ausgabe: SALAMI_DEMO_OUT (Standard: dist-website-demo/) mit index.html, relativen Pfaden
 * und nur den public-Dateien, die die Demo braucht – der Ordner kann unverändert neben die
 * Website gelegt werden.
 */
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { resolve, join, relative, extname } from "path";
import { readFileSync, writeFileSync, readdirSync, statSync, rmSync, renameSync } from "fs";

const pkg = JSON.parse(readFileSync(resolve(__dirname, "package.json"), "utf-8")) as {
  dependencies: Record<string, string>;
};
const skinRendererVersion = pkg.dependencies["@noriskclient/nrc-skin-renderer"];

// @ts-expect-error process is a nodejs global
const outDir: string = process.env.SALAMI_DEMO_OUT ?? resolve(__dirname, "dist-website-demo");

/** public-Ordner/-Dateien, die die Demo zur Laufzeit lädt. */
const KEEP_PUBLIC = ["fonts", "themes", "skins", "capes", "icons", "emotes", "minecraft", "logo.png", "salami-logo-64.png", "background.jpeg"];

const walk = (dir: string): string[] =>
  readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });

/**
 * Räumt nach dem Build auf: unnötige public-Dateien löschen, Einstieg in index.html umbenennen
 * und absolute public-Pfade im Code ("/fonts/…") relativ machen, damit die Demo in jedem Unterordner läuft.
 */
function websiteDemoOutput(): Plugin {
  return {
    name: "salami-website-demo-output",
    apply: "build",
    closeBundle() {
      const publicDir = resolve(__dirname, "public");
      for (const name of readdirSync(publicDir)) {
        if (!KEEP_PUBLIC.includes(name)) rmSync(join(outDir, name), { recursive: true, force: true });
      }
      renameSync(join(outDir, "website-demo.html"), join(outDir, "index.html"));

      const roots = KEEP_PUBLIC.map((n) => n.replace(".", "\\.")).join("|");
      const inCode = new RegExp(`(["'\`])/(${roots})(?=[/"'\`])`, "g");
      for (const file of walk(outDir)) {
        const ext = extname(file);
        if (ext !== ".js" && ext !== ".css") continue;
        const prefix = relative(join(file, ".."), outDir).replace(/\\/g, "/") || ".";
        const code = readFileSync(file, "utf-8");
        const fixed =
          ext === ".js"
            ? code.replace(inCode, `$1./$2`)
            : code.replace(new RegExp(`url\\((["']?)/(${roots})(?=[/"')])`, "g"), `url($1${prefix}/$2`);
        if (fixed !== code) writeFileSync(file, fixed);
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), websiteDemoOutput()],
  base: "./",
  define: {
    __SKIN_RENDERER_VERSION__: JSON.stringify(skinRendererVersion),
  },
  resolve: {
    dedupe: ["three", "react", "react-dom", "@react-three/fiber", "@react-three/drei"],
    alias: [
      {
        find: /^@iconify\/react$/,
        replacement: resolve(__dirname, "src/lib/iconify.tsx"),
      },
    ],
  },
  build: {
    outDir,
    emptyOutDir: true,
    target: "es2022",
    chunkSizeWarningLimit: 6000,
    rollupOptions: {
      input: { demo: resolve(__dirname, "website-demo.html") },
    },
  },
});
