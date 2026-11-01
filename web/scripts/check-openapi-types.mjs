/**
 * Contract check between the web and the API. It fails when a path the web calls, or a property name the web reads, is
 * missing from the OpenAPI document the API generates.
 *
 * Where the document comes from:
 *   OPENAPI_FILE=<path>  the file to read (api/target/openapi.json, written by OpenApiSnapshotIT)
 *   otherwise            GET <NEXT_PUBLIC_API_BASE_URL or http://localhost:8080>/v3/api-docs from the running API
 *
 * When the document cannot be read at all (no file, or the API is down), the check skips with a warning, as local dev
 * without a backend expects. OPENAPI_STRICT=1 turns that skip into a failure. A document that is read but lacks a path or
 * a property always fails.
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ??
  "http://localhost:8080";
const strict = process.env.OPENAPI_STRICT === "1";

// Each path is confirmed against its controller's mapping (the @GetMapping/@PutMapping string). The
// /live-exams/{id}/answer-pdf path is declared twice (GET in LiveExamEvaluationController, POST in LiveExamSittingController);
// the path is present either way.
const requiredPaths = [
  "/api/v1/auth/login",
  "/api/v1/auth/refresh",
  "/api/v1/me",
  "/api/v1/admins",
  "/api/v1/students",
  // Staff reviews (LiveExamReviewStaffController).
  "/api/v1/staff/reviews",
  "/api/v1/staff/reviews/counts",
  "/api/v1/staff/reviews/pending-count",
  "/api/v1/staff/reviews/{id}/status",
  "/api/v1/staff/reviews/{id}/featured",
  // Student and public reviews (LiveExamReviewStudentController, LiveExamReviewPublicController).
  "/api/v1/me/reviews",
  "/api/v1/me/reviews/{kind}/{id}",
  "/api/v1/public/reviews/{kind}/{id}",
  "/api/v1/public/reviews/featured",
  // Announcements (AnnouncementController, StudentInboxController).
  "/api/v1/announcements",
  "/api/v1/announcements/preview",
  "/api/v1/announcements/{id}/withdraw",
  "/api/v1/me/announcements",
  // The answer script (LiveExamSittingController POST, LiveExamEvaluationController GET).
  "/api/v1/live-exams/{id}/answer-pdf",
];

// Schema names are the Java record names, which springdoc uses as components.schemas keys. Each entry lists the property
// names the web reads from that record.
const requiredProperties = [
  // LiveExamSittingService.StudentExam: the student's exam read. GET /api/v1/live-exams/{id} is declared to return Object
  // (LiveExamController), so springdoc documents it as type object and this record is not in the spec at all. The check
  // falls back to the record's declaration in its Java source; that is weaker than the spec check and is reported as such.
  {
    schema: "StudentExam",
    properties: ["reviewOpensAt", "answerStored"],
    javaSource: "../../api/src/main/java/com/coachingcenter/api/exams/LiveExamSittingService.java",
  },
  // LiveExamSittingService.ReviewedResult: the reviewed result.
  { schema: "ReviewedResult", properties: ["reviewOpensAt"] },
  // LiveExamPublicService.PublicExam and PublicModule: the public exam and package views.
  { schema: "PublicExam", properties: ["ratingAverage", "ratingCount"] },
  { schema: "PublicModule", properties: ["ratingAverage", "ratingCount"] },
  // AnnouncementService.AnnouncementRow: the announcement history rows.
  { schema: "AnnouncementRow", properties: ["withdrawn", "examId"] },
  // LiveExamReviewService.Mine: the student's review state on one target.
  { schema: "Mine", properties: ["canReview", "reviewed"] },
  // LiveExamReviewModerationService.Counts: the staff review tab counts.
  { schema: "Counts", properties: ["pending", "approved", "hidden", "featured"] },
];

async function readSpec() {
  const file = process.env.OPENAPI_FILE;
  if (file) {
    const path = resolve(file);
    try {
      return { source: path, spec: JSON.parse(readFileSync(path, "utf8")) };
    } catch (error) {
      return { source: path, error: `cannot read ${path} (${error.message})` };
    }
  }
  const url = `${apiBaseUrl}/v3/api-docs`;
  try {
    const res = await fetch(url);
    if (!res.ok) {
      return { source: url, error: `HTTP ${res.status} from ${url}` };
    }
    return { source: url, spec: await res.json() };
  } catch (error) {
    return { source: url, error: `API not reachable at ${url} (${error.message})` };
  }
}

/** The properties a Java record declares in its header (between `record Name(` and `) {`), or null when it is not found. */
function recordProperties(javaText, record) {
  const start = javaText.search(new RegExp(`\\brecord\\s+${record}\\s*\\(`));
  if (start < 0) {
    return null;
  }
  return javaText.slice(start, javaText.indexOf(") {", start));
}

async function main() {
  const { source, spec, error } = await readSpec();
  if (!spec) {
    if (strict) {
      console.error(`check:openapi — ${error}. OPENAPI_STRICT=1, so the drift check cannot be skipped.`);
      process.exit(1);
    }
    console.warn(`check:openapi — ${error}; skipping drift check.`);
    return;
  }

  const paths = spec.paths ?? {};
  const schemas = spec.components?.schemas ?? {};
  const failures = [];

  for (const path of requiredPaths) {
    if (!Object.hasOwn(paths, path)) {
      failures.push(`path ${path}`);
    }
  }
  const sourceChecked = [];
  for (const { schema, properties, javaSource } of requiredProperties) {
    const definition = Object.hasOwn(schemas, schema) ? schemas[schema] : undefined;
    if (!definition) {
      if (!javaSource) {
        failures.push(`schema ${schema} (not in components.schemas: renamed, or inlined in a response)`);
        continue;
      }
      const javaPath = fileURLToPath(new URL(javaSource, import.meta.url));
      let header = null;
      try {
        header = recordProperties(readFileSync(javaPath, "utf8"), schema);
      } catch (readError) {
        failures.push(`schema ${schema} (not in the spec, and its source ${javaPath} cannot be read: ${readError.message})`);
        continue;
      }
      if (header === null) {
        failures.push(`record ${schema} (not in the spec, and not declared in ${javaPath})`);
        continue;
      }
      for (const property of properties) {
        if (!new RegExp(`\\b${property}\\b`).test(header)) {
          failures.push(`property ${schema}.${property} (in ${javaPath})`);
        }
      }
      sourceChecked.push(schema);
      continue;
    }
    const names = Object.keys(definition.properties ?? {});
    for (const property of properties) {
      if (!names.includes(property)) {
        failures.push(`property ${schema}.${property}`);
      }
    }
  }
  if (failures.length) {
    console.error(`check:openapi — ${failures.length} missing from the OpenAPI document (${source}):`);
    for (const failure of failures) {
      console.error(`  - ${failure}`);
    }
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

  if (sourceChecked.length) {
    console.warn(
      `check:openapi — ${sourceChecked.join(", ")} not in the spec (documented as object); its properties were checked in the Java source only.`,
    );
  }
  console.log(
    `check:openapi — ${requiredPaths.length} paths and ${requiredProperties.length} schemas checked against ${source}: OK.`,
  );
}

main().catch((error) => {
  console.error("check:openapi — unexpected error:", error);
  process.exit(1);
});
