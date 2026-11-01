import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { QueryClient } from "@tanstack/react-query";
import { useInvalidateMarking, usePublishPaper, useSaveMarking, useSetReady } from "@/lib/api/exam/staff-marking";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

// The results report and the release panel key on these; a marking change must mark them stale at once.
const RESULT_KEYS = [
  ["staffResults", "report", "e1", {}],
  ["staffResults", "release", "e1"],
] as const;

/** One button per marking action, so each test runs the action it checks. */
function Actions() {
  const save = useSaveMarking("p1");
  const publish = usePublishPaper("p1");
  const ready = useSetReady("p1");
  const invalidate = useInvalidateMarking();
  return (
    <div>
      <button onClick={() => save.mutate({ marks: 30, comment: "", file: null })}>save</button>
      <button onClick={() => publish.mutate()}>publish</button>
      <button onClick={() => ready.mutate(true)}>ready</button>
      <button onClick={() => void invalidate()}>bulk</button>
    </div>
  );
}

function seedResults(queryClient: QueryClient) {
  for (const key of RESULT_KEYS) {
    queryClient.setQueryData(key, { counts: 1 });
  }
  // A key outside the marking reads: it must not be touched by any of these actions.
  queryClient.setQueryData(["staffCatalog", "unrelated"], { counts: 1 });
}

function invalidatedStates(queryClient: QueryClient) {
  return RESULT_KEYS.map((key) => queryClient.getQueryState(key)?.isInvalidated);
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("marking changes mark the results stale (P1)", () => {
  it("a saved mark invalidates the results report and the release panel", async () => {
    mockApi({});
    // Uploads go through XMLHttpRequest, so answer the save as a success.
    const send = vi.spyOn(XMLHttpRequest.prototype, "send").mockImplementation(function (this: XMLHttpRequest) {
      Object.defineProperty(this, "status", { value: 200 });
      Object.defineProperty(this, "responseText", { value: JSON.stringify({ id: "p1", score: 30 }) });
      this.onload?.(new ProgressEvent("load"));
    });
    try {
      const { queryClient } = renderWithProviders(<Actions />, { user: fixtures.masterAdmin });
      seedResults(queryClient);
      await userEvent.click(screen.getByRole("button", { name: "save" }));
      await waitFor(() => expect(invalidatedStates(queryClient)).toEqual([true, true]));
      expect(queryClient.getQueryState(["staffCatalog", "unrelated"])?.isInvalidated).toBe(false);
    } finally {
      send.mockRestore();
    }
  });

  it("publishing one paper invalidates the results", async () => {
    mockApi({ "POST /api/v1/live-exam-participants/p1/publish": { id: "p1", published: true } });
    const { queryClient } = renderWithProviders(<Actions />, { user: fixtures.masterAdmin });
    seedResults(queryClient);
    await userEvent.click(screen.getByRole("button", { name: "publish" }));
    await waitFor(() => expect(invalidatedStates(queryClient)).toEqual([true, true]));
  });

  it("setting the ready flag invalidates the results", async () => {
    mockApi({ "POST /api/v1/live-exam-participants/p1/ready": { id: "p1", readyForRelease: true } });
    const { queryClient } = renderWithProviders(<Actions />, { user: fixtures.masterAdmin });
    seedResults(queryClient);
    await userEvent.click(screen.getByRole("button", { name: "ready" }));
    await waitFor(() => expect(invalidatedStates(queryClient)).toEqual([true, true]));
  });

  it("a bulk release, which uses the shared invalidation, invalidates the results", async () => {
    mockApi({});
    const { queryClient } = renderWithProviders(<Actions />, { user: fixtures.masterAdmin });
    seedResults(queryClient);
    await userEvent.click(screen.getByRole("button", { name: "bulk" }));
    await waitFor(() => expect(invalidatedStates(queryClient)).toEqual([true, true]));
  });
});
