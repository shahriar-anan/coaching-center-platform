import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamRow } from "@/components/student/catalog/exam-row";
import { PackageCover } from "@/components/ui/package/package-cover";
import { CoverUploader } from "@/components/staff/catalog/cover-uploader";
import { mockApi, renderWithProviders } from "../helpers";

describe("package cover", () => {
  it("shows the brand fallback with the initials when no image was uploaded", () => {
    renderWithProviders(<PackageCover src={null} alt="" type="MCQ" title="BJS Preliminary Model Test" />);
    expect(screen.getByText("BP")).toBeInTheDocument();
    expect(screen.getByText("MCQ")).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });

  it("shows the uploaded image when a cover path exists", () => {
    renderWithProviders(
      <PackageCover src="http://api.test/api/v1/public/live-exam-modules/m1/cover?v=1" alt="Cover of BJS" type={null} title="BJS" />,
    );
    const image = screen.getByRole("img", { name: "Cover of BJS" });
    expect(image).toHaveAttribute("src", "http://api.test/api/v1/public/live-exam-modules/m1/cover?v=1");
  });
});

describe("exam row", () => {
  it("keeps the status or price visible next to a long summary", () => {
    renderWithProviders(
      <ExamRow
        href="/exam/e1"
        title="CrPC written"
        summary={"A very long description ".repeat(12)}
        right={<span>৳৩০০</span>}
      />,
    );
    expect(screen.getByRole("link", { name: /CrPC written/ })).toHaveAttribute("href", "/exam/e1");
    expect(screen.getByText("৳৩০০")).toBeInTheDocument();
    const summary = screen.getByText(/A very long description/);
    expect(summary.className).toContain("line-clamp-2");
  });
});

describe("cover uploader", () => {
  const pkg = {
    id: "m1",
    categoryId: "c1",
    titleEn: "BJS",
    titleBn: "বিজেএস",
    descriptionEn: null,
    descriptionBn: null,
    accessMode: "OPEN",
    status: "DRAFT",
    finallySaved: false,
    position: 1,
    price: null,
    coverUrl: null,
  };

  it("rejects a file that is not JPG, PNG or WebP before any request is sent", async () => {
    const api = mockApi({});
    renderWithProviders(<CoverUploader module={pkg as never} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.setup({ applyAccept: false }).upload(input, new File(["%PDF"], "cover.pdf", { type: "application/pdf" }));
    expect(await screen.findByText("JPG, PNG বা WebP ছবি দিন।")).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === "POST")).toHaveLength(0);
  });

  it("rejects an image over 2 MB before any request is sent", async () => {
    const api = mockApi({});
    renderWithProviders(<CoverUploader module={pkg as never} />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], "big.png", { type: "image/png" });
    await userEvent.setup({ applyAccept: false }).upload(input, big);
    expect(await screen.findByText("ছবিটি ২ MB-এর বেশি।")).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === "POST")).toHaveLength(0);
  });
});
