// Checks that the pdf.js the site ships opens PDFs on an engine without the newest browser APIs (22 Nov 2026: the modern
// build failed on every PDF below about Chrome 140 with "hashOriginal.toHex is not a function").
//
//   node web/scripts/check-pdfjs-old-engine.mjs [file.pdf ...]
//
// Node's V8 stands in for an older phone browser. The script removes the APIs older browsers lack, so the check stays
// meaningful as Node gets newer, then opens each PDF with the shipped legacy build and the shipped worker, and reads
// every page. With no files it uses the routine PDFs in the API's test fixtures. Exit code 1 on any failure.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const root = path.resolve(web, "..");

// Browsers older than about Chrome 140 / Safari 18.2 lack these. The legacy build must not need them.
const removed = [];
for (const [owner, name] of [
  [Uint8Array.prototype, "toHex"],
  [Uint8Array.prototype, "toBase64"],
  [Uint8Array, "fromBase64"],
  [Uint8Array, "fromHex"],
  [Map.prototype, "getOrInsert"],
  [Map.prototype, "getOrInsertComputed"],
  [WeakMap.prototype, "getOrInsert"],
  [WeakMap.prototype, "getOrInsertComputed"],
  [Math, "sumPrecise"],
]) {
  if (name in owner) {
    delete owner[name];
    removed.push(name);
  }
}

const files = process.argv.slice(2);
if (files.length === 0) {
  const fixtures = path.join(root, "api", "src", "test", "resources", "fixtures", "routine");
  if (existsSync(fixtures)) {
    for (const name of readdirSync(fixtures)) {
      if (name.toLowerCase().endsWith(".pdf")) files.push(path.join(fixtures, name));
    }
  }
}
if (files.length === 0) {
  console.error("check-pdfjs-old-engine: no PDFs to check");
  process.exit(1);
}

const pdfjs = await import(pathToFileURL(path.join(web, "node_modules", "pdfjs-dist", "legacy", "build", "pdf.mjs")).href);
// The worker the site serves (public/), so a stale or modern copy there fails this check.
pdfjs.GlobalWorkerOptions.workerSrc = pathToFileURL(path.join(web, "public", "pdf.worker.min.mjs")).href;

let failed = 0;
for (const file of files) {
  try {
    const task = pdfjs.getDocument({ data: new Uint8Array(readFileSync(file)), isEvalSupported: false });
    const doc = await task.promise;
    for (let index = 1; index <= doc.numPages; index++) {
      const page = await doc.getPage(index);
      await page.getOperatorList();
      await page.getTextContent();
    }
    console.log(`ok     ${path.basename(file)} (${doc.numPages} pages)`);
    await task.destroy();
  } catch (error) {
    failed++;
    console.log(`FAILED ${path.basename(file)}: ${error?.name}: ${error?.message}`);
  }
}
console.log(`pdf.js ${pdfjs.version}, APIs removed for the check: ${removed.join(", ") || "none (this Node lacks them already)"}`);
process.exit(failed ? 1 : 0);
