import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../helpers";
import CodePage from "@/app/(auth)/code/page";
import ActivatePage from "@/app/(auth)/activate/page";
import ResetPasswordPage from "@/app/(auth)/reset-password/page";
import { copyText, readCodeFromHash } from "@/app/(auth)/_components/code-hash";

function setHash(hash: string) {
  window.history.replaceState(null, "", `/${hash}`);
}

afterEach(() => {
  window.history.replaceState(null, "", "/");
  vi.restoreAllMocks();
});

describe("reading a code from a mail link", () => {
  it("takes #code= or #c= and ignores anything that is not shaped like a code", () => {
    setHash("#code=K7M2Q9RX");
    expect(readCodeFromHash()).toBe("K7M2Q9RX");
    setHash("#c=482913&for=reset");
    expect(readCodeFromHash()).toBe("482913");
    setHash("#code=<script>");
    expect(readCodeFromHash()).toBeNull();
    setHash("");
    expect(readCodeFromHash()).toBeNull();
  });

  it("copies with the clipboard when the browser has one, and says false when it refuses", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    await expect(copyText("K7M2Q9RX")).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("K7M2Q9RX");
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockRejectedValue(new Error("denied")) } });
    Object.assign(document, { execCommand: vi.fn().mockReturnValue(false) });
    await expect(copyText("K7M2Q9RX")).resolves.toBe(false);
  });
});

describe("the page behind a mail's Copy code button", () => {
  it("shows the code large, copies it on the button, and links on to the right step", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    setHash("#c=K7M2Q9RX&for=activate");
    renderWithProviders(<CodePage />);

    expect(await screen.findByText("K7M2Q9RX")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "কোড কপি করুন" }));
    expect(writeText).toHaveBeenCalledWith("K7M2Q9RX");
    expect(await screen.findByRole("button", { name: "কপি হয়েছে" })).toBeInTheDocument();
    expect(screen.getByText("কোডটি কপি হয়েছে। এখন পরের ধাপে বসান।")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "অ্যাকাউন্ট চালু করুন" })).toHaveAttribute("href", "/activate#code=K7M2Q9RX");
  });

  it("removes the code from the address bar once it has been read", async () => {
    setHash("#c=K7M2Q9RX&for=reset");
    renderWithProviders(<CodePage />);
    await screen.findByText("K7M2Q9RX");
    expect(window.location.hash).toBe("");
    expect(screen.getByRole("link", { name: "নতুন পাসওয়ার্ড দিন" })).toHaveAttribute("href", "/reset-password#code=K7M2Q9RX");
  });

  it("says so when the link has no code, and offers the sign-in page", async () => {
    setHash("");
    renderWithProviders(<CodePage />);
    expect(await screen.findByText("কোড পাওয়া যায়নি")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "লগইন পাতায় যান" })).toHaveAttribute("href", "/login");
  });

  it("tells the person when the browser would not copy", async () => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockRejectedValue(new Error("denied")) } });
    Object.assign(document, { execCommand: vi.fn().mockReturnValue(false) });
    setHash("#c=K7M2Q9RX&for=verify");
    renderWithProviders(<CodePage />);
    await userEvent.click(await screen.findByRole("button", { name: "কোড কপি করুন" }));
    expect(await screen.findByText("কপি করা যায়নি। কোডটি চেপে ধরে কপি করুন।")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "অ্যাকাউন্ট পাতা খুলুন" })).toHaveAttribute("href", "/account");
  });
});

describe("a mail link fills the code in", () => {
  it("activation: the code field arrives filled and the fragment is gone", async () => {
    setHash("#code=K7M2Q9RX");
    renderWithProviders(<ActivatePage />);
    await waitFor(() => expect(screen.getByLabelText(/কোড/, { selector: "input" })).toHaveValue("K7M2Q9RX"));
    expect(window.location.hash).toBe("");
  });

  it("reset: the code field arrives filled", async () => {
    setHash("#code=P4R8W2YN");
    renderWithProviders(<ResetPasswordPage />);
    await waitFor(() => expect(screen.getByLabelText(/কোড/, { selector: "input" })).toHaveValue("P4R8W2YN"));
  });

  it("activation: no fragment leaves the field empty", async () => {
    setHash("");
    renderWithProviders(<ActivatePage />);
    expect(await screen.findByLabelText(/কোড/, { selector: "input" })).toHaveValue("");
  });
});
