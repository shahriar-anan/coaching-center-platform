import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useDraftForm } from "@/lib/hooks/use-draft-form";
import { useListPrefs } from "@/lib/hooks/use-list-prefs";
import { useUnsavedChangesGuard } from "@/lib/hooks/use-unsaved-changes-guard";

beforeEach(() => window.localStorage.clear());
afterEach(() => window.localStorage.clear());

describe("useListPrefs", () => {
  it("starts empty and stores what is set", () => {
    const { result } = renderHook(() => useListPrefs("exams"));
    expect(result.current[0]).toEqual({});
    act(() => result.current[1]({ pageSize: 50, status: "paid" }));
    expect(result.current[0]).toEqual({ pageSize: 50, status: "paid" });
    expect(JSON.parse(window.localStorage.getItem("listPrefs:exams") ?? "null")).toEqual({
      pageSize: 50,
      status: "paid",
    });
  });

  it("reads the stored value for the same key on a new mount", () => {
    window.localStorage.setItem("listPrefs:students", JSON.stringify({ pageSize: 20 }));
    const { result } = renderHook(() => useListPrefs("students"));
    expect(result.current[0]).toEqual({ pageSize: 20 });
  });

  it("keeps different keys apart", () => {
    const { result } = renderHook(() => useListPrefs("a"));
    act(() => result.current[1]({ pageSize: 10 }));
    const other = renderHook(() => useListPrefs("b"));
    expect(other.result.current[0]).toEqual({});
  });

  it("falls back to empty prefs on bad stored JSON", () => {
    window.localStorage.setItem("listPrefs:bad", "{not json");
    const { result } = renderHook(() => useListPrefs("bad"));
    expect(result.current[0]).toEqual({});
  });
});

describe("useDraftForm", () => {
  it("writes each change to the draft and reports no draft on a fresh start", () => {
    const { result } = renderHook(() => useDraftForm("question:1", { text: "" }));
    expect(result.current.hasDraft).toBe(false);
    act(() => result.current.setValue({ text: "প্রশ্ন" }));
    expect(result.current.value).toEqual({ text: "প্রশ্ন" });
    expect(JSON.parse(window.localStorage.getItem("draft:question:1") ?? "null")).toEqual({ text: "প্রশ্ন" });
  });

  it("offers the saved draft after a reload and restores it", () => {
    window.localStorage.setItem("draft:question:2", JSON.stringify({ text: "খসড়া" }));
    const { result } = renderHook(() => useDraftForm("question:2", { text: "" }));
    expect(result.current.hasDraft).toBe(true);
    act(() => result.current.restore());
    expect(result.current.value).toEqual({ text: "খসড়া" });
    expect(result.current.hasDraft).toBe(false);
  });

  it("removes the draft on clear, after a successful save", () => {
    window.localStorage.setItem("draft:question:3", JSON.stringify({ text: "x" }));
    const { result } = renderHook(() => useDraftForm("question:3", { text: "" }));
    act(() => result.current.clear());
    expect(window.localStorage.getItem("draft:question:3")).toBeNull();
  });
});

describe("useUnsavedChangesGuard", () => {
  it("prevents unload only while dirty", () => {
    const { rerender } = renderHook(({ dirty }) => useUnsavedChangesGuard(dirty), {
      initialProps: { dirty: false },
    });
    const clean = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(clean);
    expect(clean.defaultPrevented).toBe(false);

    rerender({ dirty: true });
    const dirty = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(dirty);
    expect(dirty.defaultPrevented).toBe(true);

    rerender({ dirty: false });
    const again = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(again);
    expect(again.defaultPrevented).toBe(false);
  });
});

describe("useDraftForm: the offered draft is kept (FN-9)", () => {
  it("keeps the saved draft while the user types, and offers the newest edit after a reload", () => {
    window.localStorage.setItem("draft:question:9", JSON.stringify({ text: "আগের খসড়া" }));
    const { result, unmount } = renderHook(() => useDraftForm("question:9", { text: "" }));
    expect(result.current.hasDraft).toBe(true);
    act(() => result.current.setValue({ text: "নতুন লেখা" }));
    expect(result.current.hasDraft).toBe(true);
    expect(JSON.parse(window.localStorage.getItem("draft:question:9") ?? "null")).toEqual({ text: "আগের খসড়া" });
    unmount();

    const reloaded = renderHook(() => useDraftForm("question:9", { text: "" }));
    expect(reloaded.result.current.hasDraft).toBe(true);
    act(() => reloaded.result.current.restore());
    expect(reloaded.result.current.value).toEqual({ text: "নতুন লেখা" });
  });

  it("restores the older draft when the user does not type first", () => {
    window.localStorage.setItem("draft:question:10", JSON.stringify({ text: "খসড়া" }));
    const { result } = renderHook(() => useDraftForm("question:10", { text: "" }));
    act(() => result.current.restore());
    expect(result.current.value).toEqual({ text: "খসড়া" });
    expect(result.current.hasDraft).toBe(false);
  });
});
