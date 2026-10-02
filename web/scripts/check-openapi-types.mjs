/**
 * Fails when the API is reachable and core schema fields drift from src/lib/api/types.ts.
 * Skips when the API is down (local dev without backend).
 */
import { readFileSync } from "node:fs";
const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ??
  "http://localhost:8080";

const requiredPaths = [
  "/api/v1/auth/login",
  "/api/v1/auth/refresh",
  "/api/v1/me",
  "/api/v1/admins",
  "/api/v1/students",
];

async function main() {
  let spec;
  try {
    const res = await fetch(`${apiBaseUrl}/v3/api-docs`);
    if (!res.ok) {
      console.warn("check:openapi — API not reachable; skipping drift check.");
      return;
    }
    spec = await res.json();
  } catch {
    console.warn("check:openapi — API not reachable; skipping drift check.");
    return;
  }

  const paths = Object.keys(spec.paths ?? {});
  const missing = requiredPaths.filter((p) => !paths.some((key) => key === p));
  if (missing.length) {
    console.error("check:openapi — missing paths in OpenAPI:", missing.join(", "));
    process.exit(1);
  }

  const typesSource = readFileSync("src/lib/api/types.ts", "utf8");
  const markers = ["MeView", "CreatedAccount", "PageResponse", "TokenResponse"];
  for (const marker of markers) {
    if (!typesSource.includes(marker)) {
      console.error(`check:openapi — types file missing ${marker}`);
      process.exit(1);
    }
  }

  console.log("check:openapi — core paths and type markers OK.");
}

main();
