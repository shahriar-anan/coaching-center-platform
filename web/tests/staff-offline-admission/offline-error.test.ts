import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { offlineErrorText } from "@/components/staff/offline/offline-error";

/** The sentence for each failure, keyed the way the test translates them: the key itself. */
const t = (key: string) => key;

function refusal(code: string, status = 409): ApiError {
  return new ApiError({ code, message: "English message from the API", requestId: "r1", fieldErrors: [] } as never, status);
}

describe("offlineErrorText (review F13, W4)", () => {
  it("names a clear refusal instead of blaming another staff member", () => {
    expect(offlineErrorText(refusal("ADMISSION_STATE"), t)).toBe("staffOffline.errors.refused");
    expect(offlineErrorText(refusal("GRANT_ALREADY_ACTIVE"), t)).toBe("staffOffline.errors.refused");
    expect(offlineErrorText(refusal("OPEN_ADMISSION"), t)).toBe("staffOffline.errors.refused");
  });

  it("keeps the conflict sentence for any other 409", () => {
    expect(offlineErrorText(refusal("STALE_VERSION"), t)).toBe("staffOffline.errors.conflict");
  });

  it("keeps the sentences for denied, checks and anything else", () => {
    expect(offlineErrorText(refusal("ANY", 403), t)).toBe("staffOffline.errors.denied");
    expect(offlineErrorText(refusal("ANY", 400), t)).toBe("staffOffline.errors.check");
    expect(offlineErrorText(new Error("network"), t)).toBe("staffOffline.errors.generic");
  });
});
