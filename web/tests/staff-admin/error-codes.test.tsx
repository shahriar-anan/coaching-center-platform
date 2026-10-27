import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StudentAccessSection } from "@/components/staff/access/student-access-section";
import { DeleteAccountButton } from "@/components/staff/status/account-controls";
import bnStaffAdmin from "@/i18n/messages/bn/staffAdmin";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import "./copy-setup";

/*
 * An error the screen does not know gets the generic message in Bangla. The raw
 * code and the API's own text never reach the page (AG-09).
 */

const studentId = "11111111-1111-4111-8111-111111111111";
const RAW_CODE = "SOMETHING_NEW";
const RAW_TEXT = "Internal detail: table grant_v2 locked";

const access = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
const modules = {
  content: [{ id: "aaaaaaaa-0000-4000-8000-000000000001", titleEn: "19th BJS", titleBn: "১৯তম বিজেএস", status: "PUBLISHED" }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

describe("unknown error codes on the admin screens", () => {
  it("the grant form shows the generic message, not the raw code", async () => {
    mockApi({
      [`GET /api/v1/students/${studentId}/live-exam-access`]: access,
      "GET /api/v1/live-exam-modules": modules,
      "POST /api/v1/live-exam-grants": apiError(500, RAW_CODE, RAW_TEXT),
    });
    renderWithProviders(<StudentAccessSection studentId={studentId} studentName="রহিম" />, {
      user: fixtures.masterAdmin,
      locale: "bn",
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: bnStaffAdmin.access.grantOpen }));
    await user.selectOptions(screen.getByLabelText(/^মডিউল/), modules.content[0].id);
    await user.type(screen.getByLabelText(/^কারণ/), "ভর্তির জন্য");
    await user.click(screen.getByRole("button", { name: bnStaffAdmin.grant.submit }));

    expect(await screen.findByText(bnStaffAdmin.errors.generic)).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(RAW_CODE))).not.toBeInTheDocument();
    expect(screen.queryByText(new RegExp(RAW_TEXT))).not.toBeInTheDocument();
  });

  it("the delete dialog shows the generic message, not the raw code", async () => {
    mockApi({
      [`DELETE /api/v1/students/${studentId}`]: apiError(500, RAW_CODE, RAW_TEXT),
    });
    renderWithProviders(<DeleteAccountButton kind="students" id={studentId} name="রহিম" />, {
      user: fixtures.masterAdmin,
      locale: "bn",
    });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: bnStaffAdmin.account.deleteAction }));
    await user.type(screen.getByRole("textbox"), "রহিম");
    const confirm = screen.getAllByRole("button", { name: bnStaffAdmin.account.deleteConfirm }).at(-1)!;
    await user.click(confirm);

    await waitFor(() => expect(screen.getByText(bnStaffAdmin.errors.generic)).toBeInTheDocument());
    expect(screen.queryByText(new RegExp(RAW_CODE))).not.toBeInTheDocument();
    expect(screen.queryByText(new RegExp(RAW_TEXT))).not.toBeInTheDocument();
  });
});
