import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionFormView } from "@/components/student/admission/admission-form";
import type { AdmissionForm } from "@/lib/api/exam/offline-admission";
import { ErrorBody, fixtures, mockApi, renderWithProviders } from "../helpers";
import { admissionView, completeForm } from "./admission-fixture";

/** A 400 VALIDATION_FAILED answer with the API's per-field errors (plan D6). */
function validationFailed(fieldErrors: { field: string; message: string }[]) {
  return new ErrorBody(400, {
    code: "VALIDATION_FAILED",
    message: "Request validation failed.",
    fieldErrors,
  } as unknown as { code: string; message: string });
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\/]/g, "\\$&");
}

const FIELD_LABELS = [
  "Full name",
  "Father's name",
  "Mother's name",
  "Date of birth",
  "Email",
  "University",
  "Semester / batch",
  "National ID number",
  "Mobile number",
  "Emergency mobile number",
  "Holding no. / hall / mess",
  "Thana",
  "District",
  "Present address",
  "Permanent address",
];

function renderForm(form: AdmissionForm = {}, state: "AWAITING_FORM" | "CHANGES_REQUESTED" = "AWAITING_FORM", extra = {}) {
  return renderWithProviders(<AdmissionFormView admission={admissionView(state, extra, form)} />, {
    user: fixtures.student,
    locale: "en",
    route: "/admission",
  });
}

describe("the admission form shows every required field (plan 6.2)", () => {
  it("renders each field with its label, the right keyboard, and the gender pair", () => {
    mockApi({});
    renderForm();

    for (const label of FIELD_LABELS) {
      expect(screen.getByLabelText(new RegExp(`^${escapeRegExp(label)}`))).toBeInTheDocument();
    }
    expect(screen.getByRole("group", { name: /Gender/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Male" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Female" })).toBeInTheDocument();

    expect(screen.getByLabelText(/^National ID number/)).toHaveAttribute("inputmode", "numeric");
    expect(screen.getByLabelText(/^Mobile number/)).toHaveAttribute("inputmode", "numeric");
    expect(screen.getByLabelText(/^Emergency mobile number/)).toHaveAttribute("inputmode", "numeric");
    expect(screen.getByLabelText(/^Email/)).toHaveAttribute("type", "email");
    expect(screen.getByLabelText(/^Date of birth/)).toHaveAttribute("type", "date");
  });

  it("offers a camera picker and a gallery picker, both limited to images", () => {
    mockApi({});
    const { container } = renderForm();

    expect(screen.getByRole("button", { name: "Take a photo" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Choose from gallery" })).toBeInTheDocument();
    const camera = container.querySelector('input[type="file"][capture="environment"]');
    const gallery = container.querySelector('input[type="file"]:not([capture])');
    expect(camera).toHaveAttribute("accept", "image/*");
    expect(gallery).toHaveAttribute("accept", "image/*");
  });
});

describe("gender is two large buttons", () => {
  it("shows the chosen gender as pressed", async () => {
    const user = userEvent.setup();
    mockApi({ "PUT /api/v1/me/admission/form": () => admissionView("AWAITING_FORM") });
    renderForm();

    const male = screen.getByRole("button", { name: "Male" });
    const female = screen.getByRole("button", { name: "Female" });
    expect(male).toHaveAttribute("aria-pressed", "false");
    expect(female).toHaveAttribute("aria-pressed", "false");

    await user.click(female);
    expect(female).toHaveAttribute("aria-pressed", "true");
    expect(male).toHaveAttribute("aria-pressed", "false");
  });
});

describe("the submit step", () => {
  it("asks for confirmation, and cancel sends nothing", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/me/admission/submit": () => admissionView("SUBMITTED") });
    renderForm(completeForm);

    await user.click(screen.getByRole("button", { name: "Submit" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Submit this form?")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(api.calls.some((call) => call.path === "/api/v1/me/admission/submit")).toBe(false);
  });

  it("sends the submit call once the student confirms", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "PUT /api/v1/me/admission/form": () => admissionView("AWAITING_FORM"),
      "POST /api/v1/me/admission/submit": () => admissionView("SUBMITTED"),
    });
    renderForm(completeForm);

    await user.click(screen.getByRole("button", { name: "Submit" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Submit" }));

    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/me/admission/submit")).toBe(true),
    );
  });

  it("puts each field error under the field it belongs to", async () => {
    const user = userEvent.setup();
    mockApi({
      "PUT /api/v1/me/admission/form": () => admissionView("AWAITING_FORM"),
      "POST /api/v1/me/admission/submit": () =>
        validationFailed([
          { field: "nid", message: "The national ID number must have 10, 13 or 17 digits." },
          { field: "email", message: "Enter a valid email address." },
          { field: "mobile", message: "Enter a Bangladeshi mobile number, such as 01711000012." },
        ]),
    });
    renderForm(completeForm);

    await user.click(screen.getByRole("button", { name: "Submit" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Submit" }));

    const nid = await screen.findByLabelText(/^National ID number/);
    await waitFor(() => expect(nid).toHaveAttribute("aria-invalid", "true"));
    expect(nid.closest("div")).toHaveTextContent("The national ID number must have 10, 13 or 17 digits.");

    const email = screen.getByLabelText(/^Email/);
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email.closest("div")).toHaveTextContent("Enter a valid email address, such as name@example.com.");

    const mobile = screen.getByLabelText(/^Mobile number/);
    expect(mobile.closest("div")).toHaveTextContent("Enter a Bangladeshi mobile number, such as 01711000012.");

    // A field without an error is not marked.
    expect(screen.getByLabelText(/^Full name/)).not.toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Check the boxes marked in red, then try again.")).toBeInTheDocument();
  });

  it("shows the photo error next to the photo", async () => {
    const user = userEvent.setup();
    mockApi({
      "PUT /api/v1/me/admission/form": () => admissionView("AWAITING_FORM"),
      "POST /api/v1/me/admission/submit": () =>
        validationFailed([
          { field: "photo", message: "Add your photo. Take one with the camera or choose one from the gallery." },
        ]),
    });
    renderForm(completeForm);

    await user.click(screen.getByRole("button", { name: "Submit" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Submit" }));

    const photoSection = (await screen.findByText("Photo")).closest("section");
    expect(photoSection).toHaveTextContent("Add your photo. Take one with the camera or choose one from the gallery.");
  });
});

describe("changes requested", () => {
  it("shows the office's reason at the top, with the form open and Submit again", () => {
    mockApi({});
    renderForm(completeForm, "CHANGES_REQUESTED", {
      changesReason: "Please upload a clearer photo.",
      submitCount: 1,
    });

    expect(screen.getByText("Changes requested")).toBeInTheDocument();
    const reason = screen.getByText("Please upload a clearer photo.");
    const firstField = screen.getByLabelText(/^Full name/);
    expect(reason.compareDocumentPosition(firstField) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    expect(firstField).toBeEnabled();
    expect(screen.getByRole("button", { name: "Submit again" })).toBeInTheDocument();
  });

  it("keeps the saved values in the boxes", () => {
    mockApi({});
    renderForm(completeForm, "CHANGES_REQUESTED", { changesReason: "Fix the NID." });
    expect(screen.getByLabelText(/^Full name/)).toHaveValue("Rahim Uddin");
    expect(screen.getByLabelText(/^National ID number/)).toHaveValue("1234567890123");
  });
});

describe("the draft", () => {
  it("saves as the student types, never more than once every two seconds, and shows Saved", async () => {
    const user = userEvent.setup();
    const sent: { fullName?: string | null; at: number }[] = [];
    mockApi({
      "PUT /api/v1/me/admission/form": (req: { body: unknown }) => {
        sent.push({ fullName: (req.body as AdmissionForm).fullName, at: Date.now() });
        return admissionView("AWAITING_FORM");
      },
    });
    renderForm();

    await user.type(screen.getByLabelText(/^Full name/), "Rahim Uddin");

    await waitFor(() => expect(sent.some((save) => save.fullName === "Rahim Uddin")).toBe(true), { timeout: 5000 });
    expect(sent.length).toBeLessThan("Rahim Uddin".length);
    for (let i = 1; i < sent.length; i++) {
      expect(sent[i].at - sent[i - 1].at).toBeGreaterThanOrEqual(1900);
    }
    expect(await screen.findByText("Saved", undefined, { timeout: 5000 })).toBeInTheDocument();
    // The box still holds what was typed, so typing was never held up.
    expect(screen.getByLabelText(/^Full name/)).toHaveValue("Rahim Uddin");
  });

  it("sends gender as MALE or FEMALE", async () => {
    const user = userEvent.setup();
    const sent: AdmissionForm[] = [];
    mockApi({
      "PUT /api/v1/me/admission/form": (req: { body: unknown }) => {
        sent.push(req.body as AdmissionForm);
        return admissionView("AWAITING_FORM");
      },
    });
    renderForm();

    await user.click(screen.getByRole("button", { name: "Female" }));
    await waitFor(() => expect(sent.some((form) => form.gender === "FEMALE")).toBe(true), { timeout: 5000 });
  });
});

describe("the photo", () => {
  it("sends a picked image to the admission photo endpoint, and rejects a file that is not an image", async () => {
    const api = mockApi({
      "PUT /api/v1/me/admission/photo": () => admissionView("AWAITING_FORM", { hasPhoto: true }),
    });
    const { container } = renderForm();
    const camera = container.querySelector('input[type="file"][capture="environment"]') as HTMLInputElement;

    fireEvent.change(camera, { target: { files: [new File(["notes"], "notes.txt", { type: "text/plain" })] } });
    expect(await screen.findByText("Choose an image file.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/me/admission/photo")).toBe(false);

    fireEvent.change(camera, { target: { files: [new File(["x"], "me.jpg", { type: "image/jpeg" })] } });
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admission/photo")).toBe(true));
    const upload = api.calls.find((call) => call.path === "/api/v1/me/admission/photo");
    expect(upload?.method).toBe("PUT");
    expect((upload?.body as FormData).get("file")).toBeInstanceOf(File);
    expect(await screen.findByText("Photo saved.")).toBeInTheDocument();
  });
});
