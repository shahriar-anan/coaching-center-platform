/* eslint-disable @next/next/no-html-link-for-pages -- the guard intercepts plain anchors; these tests need one */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useUnsavedChangesGuard } from "@/lib/hooks/use-unsaved-changes-guard";
import { UnsavedChangesDialog } from "@/components/shell/unsaved-changes-dialog";
import { settleLeaving } from "@/lib/hooks/unsaved-changes-store";
import { renderWithProviders, routerState, resetRouter } from "../helpers";

function Form({ dirty }: { dirty: boolean }) {
  useUnsavedChangesGuard(dirty);
  return (
    <>
      <a href="/staff/modules">মডিউল তালিকা</a>
      <a href="#price">মূল্য অংশ</a>
      <a href="https://example.com/help" target="_blank" rel="noreferrer">বাইরের সাহায্য</a>
      <UnsavedChangesDialog />
    </>
  );
}

const TITLE = "সেভ না করে চলে যাবেন?";

beforeEach(() => resetRouter("/staff/modules/1"));
afterEach(() => {
  // The prompt lives in a module store; close it so the next test starts clean.
  settleLeaving(false);
  vi.restoreAllMocks();
});

describe("useUnsavedChangesGuard: in-app links", () => {
  it("blocks a link click while dirty and asks first", async () => {
    renderWithProviders(<Form dirty />);
    const link = screen.getByRole("link", { name: "মডিউল তালিকা" });
    const notCancelled = fireEvent.click(link);
    expect(notCancelled).toBe(false);
    expect(routerState.push).not.toHaveBeenCalled();
    expect(await screen.findByText(TITLE)).toBeInTheDocument();
  });

  it("navigates only after the user confirms", async () => {
    renderWithProviders(<Form dirty />);
    await userEvent.click(screen.getByRole("link", { name: "মডিউল তালিকা" }));
    await userEvent.click(await screen.findByRole("button", { name: "চলে যান" }));
    await waitFor(() => expect(routerState.push).toHaveBeenCalledWith("/staff/modules"));
    expect(screen.queryByText(TITLE)).not.toBeInTheDocument();
  });

  it("stays on the form when the user cancels", async () => {
    renderWithProviders(<Form dirty />);
    await userEvent.click(screen.getByRole("link", { name: "মডিউল তালিকা" }));
    await userEvent.click(await screen.findByRole("button", { name: "বাতিল" }));
    expect(routerState.push).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByText(TITLE)).not.toBeInTheDocument());
  });

  it("lets a same-page anchor and a new-tab link through", () => {
    renderWithProviders(<Form dirty />);
    expect(fireEvent.click(screen.getByRole("link", { name: "মূল্য অংশ" }))).toBe(true);
    expect(fireEvent.click(screen.getByRole("link", { name: "বাইরের সাহায্য" }))).toBe(true);
    expect(screen.queryByText(TITLE)).not.toBeInTheDocument();
  });
});

describe("useUnsavedChangesGuard: clean forms", () => {
  it("does not interfere when the form is clean", () => {
    renderWithProviders(<Form dirty={false} />);
    expect(fireEvent.click(screen.getByRole("link", { name: "মডিউল তালিকা" }))).toBe(true);
    expect(screen.queryByText(TITLE)).not.toBeInTheDocument();
  });
});

describe("useUnsavedChangesGuard: browser back", () => {
  it("puts the page back forward and asks when dirty", async () => {
    const go = vi.spyOn(window.history, "go").mockImplementation(() => undefined);
    renderWithProviders(<Form dirty />);
    window.dispatchEvent(new PopStateEvent("popstate"));
    expect(go).toHaveBeenCalledWith(1);
    expect(await screen.findByText(TITLE)).toBeInTheDocument();
  });
});

describe("useUnsavedChangesGuard: tab close", () => {
  it("asks the browser to confirm while dirty", () => {
    renderWithProviders(<Form dirty />);
    const event = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });
});
