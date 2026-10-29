import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { fixtures, renderWithProviders } from "../helpers";

const dateField = () => screen.getByPlaceholderText("দিন/মাস/বছর");
const timeField = () => screen.getByPlaceholderText("ঘণ্টা:মিনিট");
const openCalendar = () => userEvent.click(screen.getByRole("button", { name: "ক্যালেন্ডার থেকে বেছে নিন" }));

describe("date and time input (Dhaka time, typed, 12-hour clock with AM or PM)", () => {
  it("sends the Dhaka instant once the date and time are typed", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    expect(onChange).not.toHaveBeenCalled();
    await userEvent.type(timeField(), "10:00");

    // 10:00 AM in Dhaka is 04:00 UTC.
    expect(onChange).toHaveBeenLastCalledWith("2026-11-20T04:00:00.000Z");
  });

  it("reads a 24-hour time and sets PM by itself", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    await userEvent.type(timeField(), "22:30");

    // 10:30 PM in Dhaka is 16:30 UTC, and the PM button is now pressed.
    expect(onChange).toHaveBeenLastCalledWith("2026-11-20T16:30:00.000Z");
    expect(screen.getByRole("radio", { name: "পিএম" })).toHaveAttribute("aria-checked", "true");
  });

  it("names a date that does not exist and sends nothing", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "31/02/2026");
    await userEvent.type(timeField(), "10:00");

    expect(screen.getByRole("alert")).toHaveTextContent("তারিখ দিন/মাস/বছর আকারে লিখুন।");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("names a time that is not hh:mm and sends nothing", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    await userEvent.type(timeField(), "10:75");

    expect(screen.getByRole("alert")).toHaveTextContent("সময় ঘণ্টা:মিনিট আকারে লিখুন, যেমন ১০:৩০।");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("sends null once the date and time are cleared", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value="2026-11-20T04:00:00.000Z" onChange={onChange} />, { user: fixtures.masterAdmin });

    expect(dateField()).toHaveValue("20/11/2026");
    expect(timeField()).toHaveValue("10:00");
    await userEvent.clear(dateField());
    await userEvent.clear(timeField());

    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("picks the day on the calendar, then the hour, the minute and the period in the columns", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    await openCalendar();
    const calendar = screen.getByRole("group", { name: "ক্যালেন্ডার" });
    await userEvent.click(within(calendar).getByRole("button", { name: "২৫" }));
    expect(dateField()).toHaveValue("25/11/2026");

    await userEvent.click(within(screen.getByRole("group", { name: "ঘণ্টা বেছে নিন" })).getByRole("button", { name: "১০" }));
    expect(timeField()).toHaveValue("10:00");
    // 10:00 AM in Dhaka on 25 November is 04:00 UTC.
    expect(onChange).toHaveBeenLastCalledWith("2026-11-25T04:00:00.000Z");

    await userEvent.click(within(screen.getByRole("group", { name: "মিনিট বেছে নিন" })).getByRole("button", { name: "৩০" }));
    expect(timeField()).toHaveValue("10:30");
    expect(onChange).toHaveBeenLastCalledWith("2026-11-25T04:30:00.000Z");

    await userEvent.click(within(screen.getByRole("group", { name: "এএম বা পিএম" })).getByRole("button", { name: "পিএম" }));
    expect(timeField()).toHaveValue("10:30");
    expect(screen.getByRole("radio", { name: "পিএম" })).toHaveAttribute("aria-checked", "true");
    // 10:30 PM in Dhaka on 25 November is 16:30 UTC.
    expect(onChange).toHaveBeenLastCalledWith("2026-11-25T16:30:00.000Z");
  });

  it("switches a 24-hour time to AM when the AM button is pressed", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    await userEvent.type(timeField(), "22:30");
    await userEvent.click(screen.getByRole("radio", { name: "এএম" }));

    expect(timeField()).toHaveValue("10:30");
    expect(onChange).toHaveBeenLastCalledWith("2026-11-20T04:30:00.000Z");
  });

  it("clears the picker's date and time with the Clear button", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value="2026-11-20T04:00:00.000Z" onChange={onChange} />, { user: fixtures.masterAdmin });

    await openCalendar();
    await userEvent.click(screen.getByRole("button", { name: "মুছুন" }));

    expect(dateField()).toHaveValue("");
    expect(timeField()).toHaveValue("");
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("closes the picker on Escape and gives focus back to its button", async () => {
    renderWithProviders(<DateTimeInput value={null} onChange={vi.fn()} />, { user: fixtures.masterAdmin });

    await openCalendar();
    expect(screen.getByRole("group", { name: "ক্যালেন্ডার" })).toBeInTheDocument();
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("group", { name: "ক্যালেন্ডার" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ক্যালেন্ডার থেকে বেছে নিন" })).toHaveFocus();
  });

  describe("date only (a discount end)", () => {
    it("asks for no time, and sends the last second of the typed day in Dhaka time", async () => {
      const onChange = vi.fn();
      renderWithProviders(<DateTimeInput value={null} onChange={onChange} dateOnly />, { user: fixtures.masterAdmin });

      expect(screen.queryByPlaceholderText("ঘণ্টা:মিনিট")).not.toBeInTheDocument();
      expect(screen.queryByRole("radio", { name: "পিএম" })).not.toBeInTheDocument();

      await userEvent.type(dateField(), "30/11/2026");

      // 11:59:59 PM in Dhaka (UTC+6) is 17:59:59 UTC.
      expect(onChange).toHaveBeenLastCalledWith("2026-11-30T17:59:59.000Z");
    });

    it("clears the value when the date is cleared, and the calendar has no time columns", async () => {
      const onChange = vi.fn();
      renderWithProviders(<DateTimeInput value="2026-11-30T17:59:59.000Z" onChange={onChange} dateOnly />, {
        user: fixtures.masterAdmin,
      });

      expect(dateField()).toHaveValue("30/11/2026");
      await openCalendar();
      expect(screen.queryByRole("group", { name: "ঘণ্টা বেছে নিন" })).not.toBeInTheDocument();
      await userEvent.keyboard("{Escape}");

      await userEvent.clear(dateField());
      expect(onChange).toHaveBeenLastCalledWith(null);
    });
  });
});
