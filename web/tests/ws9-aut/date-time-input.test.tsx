import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { fixtures, renderWithProviders } from "../helpers";

const dateField = () => screen.getByPlaceholderText("দিন/মাস/বছর");
const timeField = () => screen.getByPlaceholderText("ঘণ্টা:মিনিট");

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

  it("sets the time by tapping the clock: the hour, then the minutes", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    await userEvent.click(screen.getByRole("button", { name: "ঘড়ি থেকে সময় বেছে নিন" }));
    await userEvent.click(screen.getByRole("button", { name: "10" }));
    expect(timeField()).toHaveValue("10:00");

    await userEvent.click(screen.getByRole("button", { name: "30" }));
    expect(timeField()).toHaveValue("10:30");
    // 10:30 AM in Dhaka is 04:30 UTC.
    expect(onChange).toHaveBeenLastCalledWith("2026-11-20T04:30:00.000Z");
  });

  it("reads any minute from a tap on the dial, not only the fives", async () => {
    const onChange = vi.fn();
    renderWithProviders(<DateTimeInput value={null} onChange={onChange} />, { user: fixtures.masterAdmin });

    await userEvent.type(dateField(), "20/11/2026");
    await userEvent.click(screen.getByRole("button", { name: "ঘড়ি থেকে সময় বেছে নিন" }));

    // The face is 264 px across, centred at 132. 3 o'clock is an hour; 42 degrees clockwise from the top is minute 7.
    const stub = (el: HTMLElement) => {
      el.getBoundingClientRect = () => ({ left: 0, top: 0, width: 264, height: 264, right: 264, bottom: 264, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect;
    };
    stub(screen.getByRole("group", { name: "ঘণ্টা বেছে নিন" }));
    fireEvent.pointerDown(screen.getByRole("group", { name: "ঘণ্টা বেছে নিন" }), { clientX: 224, clientY: 132 });
    fireEvent.pointerUp(screen.getByRole("group", { name: "ঘণ্টা বেছে নিন" }), { clientX: 224, clientY: 132 });
    expect(timeField()).toHaveValue("03:00");

    const minutes = screen.getByRole("group", { name: "মিনিট বেছে নিন" });
    stub(minutes);
    fireEvent.pointerDown(minutes, { clientX: 199, clientY: 58 });
    expect(timeField()).toHaveValue("03:07");
  });
});
