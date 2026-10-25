import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { WrittenSitting } from "@/components/student/sitting/written-sitting";
import { ApiError } from "@/lib/api/errors";
import { renderWithProviders } from "../helpers";

const uploaded = vi.fn();

vi.mock("@/lib/api/exam/sitting", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/exam/sitting")>();
  return {
    ...actual,
    useUploadAnswer: () => ({
      mutateAsync: async (file: File) => {
        uploaded(file.name);
        throw new ApiError({ code: "DEADLINE_PASSED", message: "deadline passed", requestId: "r", timestamp: "" });
      },
      isPending: false,
    }),
  };
});

it("when the deadline passes during an upload, the drop closes and says so", async () => {
  renderWithProviders(
    <WrittenSitting
      exam={{ id: "ex-w", questionPdf: null } as never}
      deadlineAt={new Date(Date.now() + 60 * 60 * 1000).toISOString()}
      watermark="নাঈম হাসান"
      onClosed={() => undefined}
    />,
  );
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  await userEvent.upload(input, new File(["%PDF-1.4"], "late.pdf", { type: "application/pdf" }));
  await userEvent.click(screen.getByRole("button", { name: "খাতা জমা দিন" }));

  expect(uploaded).toHaveBeenCalledWith("late.pdf");
  expect(await screen.findByRole("alert")).toHaveTextContent("সময় শেষ, তাই আর খাতা জমা দেওয়া যাবে না।");
  expect(document.querySelector('input[type="file"]')).toBeNull();
});
