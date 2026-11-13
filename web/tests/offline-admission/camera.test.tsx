import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PhotoField } from "@/components/student/admission/photo-field";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/** A laptop: a fine pointer, not touch-first, with a camera API. */
function laptop(getUserMedia: unknown) {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: (query: string) => ({
      matches: query === "(pointer: fine)",
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
  Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getUserMedia } });
}

afterEach(() => {
  Reflect.deleteProperty(window, "matchMedia");
  Reflect.deleteProperty(navigator, "mediaDevices");
  vi.restoreAllMocks();
});

describe("the laptop camera (plan 6.2)", () => {
  it("takes a JPEG in the page and sends it to the photo upload, then stops the camera", async () => {
    const user = userEvent.setup();
    const track = { stop: vi.fn() };
    const getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [track] });
    laptop(getUserMedia);
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({ drawImage: vi.fn() } as unknown as CanvasRenderingContext2D);
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, "toBlob")
      .mockImplementation((callback: BlobCallback) => callback(new Blob(["jpg"], { type: "image/jpeg" })));
    const api = mockApi({
      "PUT /api/v1/me/admission/photo": { id: "adm-1", state: "AWAITING_FORM", submitCount: 0, hasPhoto: true, form: {} },
    });
    renderWithProviders(<PhotoField hasPhoto={false} />, { user: fixtures.student, locale: "en", route: "/admission" });

    await user.click(screen.getByRole("button", { name: "Take a photo" }));
    await waitFor(() => expect(getUserMedia).toHaveBeenCalledWith({ video: { facingMode: "user" } }));
    fireEvent.loadedMetadata(document.querySelector("video") as HTMLVideoElement);
    await user.click(await screen.findByRole("button", { name: "Capture" }));
    expect(toBlob).toHaveBeenCalledWith(expect.any(Function), "image/jpeg", 0.9);

    await user.click(await screen.findByRole("button", { name: "Use photo" }));
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admission/photo")).toBe(true));
    const upload = api.calls.find((call) => call.path === "/api/v1/me/admission/photo");
    expect((upload?.body as FormData).get("file")).toBeInstanceOf(File);
    expect(track.stop).toHaveBeenCalled();
  });

  it("says so and offers the file picker when the camera is refused", async () => {
    const user = userEvent.setup();
    laptop(vi.fn().mockRejectedValue(Object.assign(new Error("denied"), { name: "NotAllowedError" })));
    mockApi({});
    const { container } = renderWithProviders(<PhotoField hasPhoto={false} />, { user: fixtures.student, locale: "en", route: "/admission" });
    const gallery = container.querySelector('input[type="file"]:not([capture])') as HTMLInputElement;
    const openPicker = vi.spyOn(gallery, "click").mockImplementation(() => undefined);

    await user.click(screen.getByRole("button", { name: "Take a photo" }));
    expect(await screen.findByText(/The camera could not be started/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Choose a photo instead" }));
    expect(openPicker).toHaveBeenCalled();
  });
});
