import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useNotice } from "@/lib/notices/use-notice";
import { NOTICE_CODES, isNoticeCode, type NoticeCode } from "@/lib/notices/codes";
import en from "@/i18n/messages/en/catalog";
import bn from "@/i18n/messages/bn/catalog";
import { renderWithProviders, routerState } from "../helpers";

function Probe() {
  useNotice();
  return <p>page</p>;
}

describe("notice codes", () => {
  it("shows the toast for a known code and removes the code from the URL, keeping other parameters", async () => {
    renderWithProviders(<Probe />, { route: "/exam/e1?notice=submitted&tab=x", locale: "en" });
    expect(await screen.findByText(en.notice.submitted)).toBeInTheDocument();
    expect(routerState.replace).toHaveBeenCalledWith("/exam/e1?tab=x");
  });

  it("removes a code that is the only parameter without leaving a question mark", async () => {
    renderWithProviders(<Probe />, { route: "/exam/e1?notice=timeUp", locale: "en" });
    expect(await screen.findByText(en.notice.timeUp)).toBeInTheDocument();
    expect(routerState.replace).toHaveBeenCalledWith("/exam/e1");
  });

  it("ignores an unknown code", () => {
    renderWithProviders(<Probe />, { route: "/exam/e1?notice=bogus", locale: "en" });
    expect(routerState.replace).not.toHaveBeenCalled();
  });

  it("recognises only listed codes", () => {
    expect(isNoticeCode("submitted")).toBe(true);
    expect(isNoticeCode("constructor")).toBe(false);
    expect(isNoticeCode(null)).toBe(false);
  });

  it("has copy in both English and Bangla for every code", () => {
    for (const code of Object.keys(NOTICE_CODES) as NoticeCode[]) {
      expect(en.notice[code], `en copy for ${code}`).toBeTruthy();
      expect(bn.notice[code], `bn copy for ${code}`).toBeTruthy();
    }
  });
});
