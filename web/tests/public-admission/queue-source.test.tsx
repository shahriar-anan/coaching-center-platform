import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionQueue } from "@/components/staff/offline/admission-queue";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { counts, page, queueRow } from "../staff-offline-admission/fixtures";

describe("the admissions queue shows where each application came from (plan 6)", () => {
  it("has a Source column, and the filter asks the API for one source", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/offline-admissions": ({ url }: { url: URL }) => {
        const source = url.searchParams.get("source");
        const rows = [
          queueRow({ id: "adm-w", fullName: "Website Student", source: "PUBLIC", status: "SUBMITTED" }),
          queueRow({ id: "adm-d", fullName: "Desk Student", source: "DESK", status: "SUBMITTED" }),
        ];
        return page(source ? rows.filter((row) => row.source === source) : rows);
      },
      "GET /api/v1/offline-admissions/counts": counts(),
      "GET /api/v1/offline-batches": [],
    });
    const view = renderWithProviders(<AdmissionQueue />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/admissions?tab=SUBMITTED" });
    // The router mock does not move the search on its own; the queue reads its filter from it.
    routerState.replace.mockImplementation((href: string) => {
      routerState.search = href.split("?")[1] ?? "";
    });

    const table = await screen.findByRole("table");
    expect(within(table).getByText("Website")).toBeInTheDocument();
    expect(within(table).getByText("Desk")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Source"), "PUBLIC");
    // The mocked router does not re-render the page, so the page is rendered again to read the new filter.
    view.rerender(<AdmissionQueue />);
    await waitFor(() =>
      expect(api.calls.some((call) => call.path === "/api/v1/offline-admissions" && call.search.includes("source=PUBLIC"))).toBe(true),
    );
    expect((await screen.findAllByText("Website Student")).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.queryByText("Desk Student")).toBeNull());
  });
});
