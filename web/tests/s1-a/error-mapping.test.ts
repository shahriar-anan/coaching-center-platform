import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";

function apiError(code: string, fieldErrors?: { field: string; message: string }[]) {
  return new ApiError(
    { code, message: "server text", requestId: "req-1", timestamp: "2026-10-29T06:00:00Z", fieldErrors },
    409,
  );
}

describe("catalog error mapping", () => {
  it.each([
    "EXAM_WINDOW_INVALID",
    "QUESTION_STRUCTURE_LOCKED",
    "ANSWER_LOCKED",
    "PENDING_CHANGES_STALE",
    "NO_PENDING_CHANGES",
    "EXAM_ALREADY_IN_PACKAGE",
  ])("maps %s to its own message key", (code) => {
    expect(catalogErrorKey(apiError(code))).toBe(`staffCatalog.errors.${code}`);
  });

  it("falls back to the generic key for a code it does not know", () => {
    expect(catalogErrorKey(apiError("SOMETHING_NEW"))).toBe("staffCatalog.errors.GENERIC");
    expect(catalogErrorKey(new Error("network"))).toBe("staffCatalog.errors.GENERIC");
  });

  it("returns the message the server named for a field, and null for any other field", () => {
    const error = apiError("EXAM_WINDOW_INVALID", [{ field: "endAt", message: "End too early." }]);
    expect(fieldErrorFor(error, "endAt")).toBe("End too early.");
    expect(fieldErrorFor(error, "liveAt")).toBeNull();
    expect(fieldErrorFor(new Error("x"), "endAt")).toBeNull();
  });
});
