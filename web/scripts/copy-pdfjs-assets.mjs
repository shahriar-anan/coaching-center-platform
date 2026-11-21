// Copies the pdf.js files the viewer loads at run time out of node_modules, so they always match the installed
// pdfjs-dist version. Runs before `next dev` and `next build` (predev / prebuild).
//
// - public/pdf.worker.min.mjs: the LEGACY worker. The modern build calls browser APIs that phones older than about
//   Chrome 140 / Safari 18.2 and in-app WebViews lack (Uint8Array.toHex on every document), so no PDF opened there
//   (22 Nov 2026). The legacy build is the same pdf.js with those filled in. The viewer imports the matching legacy API.
// - public/pdfjs/{wasm,cmaps,standard_fonts,iccs}/: decoders (JBIG2 scanner images, JPEG 2000, colour profiles), CMaps
//   and standard fonts. Without them some scanned pages render blank.
import { copyFileSync, cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const pkgDir = path.join(web, "node_modules", "pdfjs-dist");
if (!existsSync(pkgDir)) {
  console.error("copy-pdfjs-assets: node_modules/pdfjs-dist is missing; run npm ci first");
  process.exit(1);
}
const { version } = JSON.parse(readFileSync(path.join(pkgDir, "package.json"), "utf8"));

const header = "/* eslint-disable -- vendored pdf.js worker, copied from pdfjs-dist; do not lint */\n";
const worker = readFileSync(path.join(pkgDir, "legacy", "build", "pdf.worker.min.mjs"), "utf8");
const workerOut = path.join(web, "public", "pdf.worker.min.mjs");
const next = header + worker;
if (!existsSync(workerOut) || readFileSync(workerOut, "utf8") !== next) {
  writeFileSync(workerOut, next);
}

const assetsOut = path.join(web, "public", "pdfjs");
rmSync(assetsOut, { recursive: true, force: true });
mkdirSync(assetsOut, { recursive: true });
for (const dir of ["wasm", "cmaps", "standard_fonts", "iccs"]) {
  cpSync(path.join(pkgDir, dir), path.join(assetsOut, dir), { recursive: true });
}
copyFileSync(path.join(pkgDir, "LICENSE"), path.join(assetsOut, "LICENSE"));
writeFileSync(path.join(assetsOut, "VERSION"), `${version}\n`);

console.log(`copy-pdfjs-assets: pdf.js ${version} (legacy worker + wasm, cmaps, standard_fonts, iccs)`);
