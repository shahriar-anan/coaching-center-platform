import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { StartConfirm } from "@/components/student/catalog/start-confirm";
import { routerState, resetRouter } from "../helpers";
import { renderWithQuery } from "./helpers";

const exam = {
  id: "exam-1",
  title: "সিআরপিসি",
  examType: "MCQ" as const,
  durationMinutes: 40,
  endAt: "2099-01-01T06:40:00Z",
  rules: "",
};

describe("starting an exam goes to the paper with the right mode", () => {
  beforeEach(() => resetRouter("/exam/exam-1"));

  it("a live start opens the paper in live mode", async () => {
    renderWithQuery(<StartConfirm open onOpenChange={() => undefined} mode="live" exam={exam} />);
    await userEvent.click(screen.getByRole("button", { name: "Start" }));
    expect(routerState.push).toHaveBeenCalledWith("/exam/exam-1/sit?start=live");
  });

  it("a later attempt opens the paper in retake mode", async () => {
    renderWithQuery(<StartConfirm open onOpenChange={() => undefined} mode="retake" exam={exam} />);
    await userEvent.click(screen.getByRole("button", { name: "Start" }));
    expect(routerState.push).toHaveBeenCalledWith("/exam/exam-1/sit?start=retake");
  });
});
