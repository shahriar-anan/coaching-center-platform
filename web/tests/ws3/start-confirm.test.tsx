import { screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { StartConfirm } from "@/components/student/catalog/start-confirm";
import { renderWithQuery } from "./helpers";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const base = {
  id: "exam-1",
  title: "সিআরপিসি",
  durationMinutes: 40,
  endAt: "2099-01-01T06:40:00Z",
  rules: "",
};

it("warns that a live MCQ start gives up the later attempt", () => {
  renderWithQuery(
    <StartConfirm open onOpenChange={() => undefined} mode="live" exam={{ ...base, examType: "MCQ" }} />,
  );
  expect(screen.getByText("If you start now, you won't get a separate practice attempt later.")).toBeInTheDocument();
  expect(screen.queryByText("You will submit your script as one PDF file.")).not.toBeInTheDocument();
});

it("uses the practice wording for a later attempt, with no forfeit line", () => {
  renderWithQuery(
    <StartConfirm open onOpenChange={() => undefined} mode="retake" exam={{ ...base, examType: "MCQ" }} />,
  );
  expect(screen.getByRole("dialog", { name: "Start the practice attempt?" })).toBeInTheDocument();
  expect(screen.getByText("This is practice. Its marks won't appear on the merit list.")).toBeInTheDocument();
  expect(
    screen.queryByText("If you start now, you won't get a separate practice attempt later."),
  ).not.toBeInTheDocument();
});

it("states the PDF rule for a written exam", () => {
  renderWithQuery(
    <StartConfirm open onOpenChange={() => undefined} mode="live" exam={{ ...base, examType: "WRITTEN" }} />,
  );
  expect(screen.getByText("You will submit your script as one PDF file.")).toBeInTheDocument();
});
