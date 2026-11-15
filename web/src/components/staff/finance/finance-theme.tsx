import type { FinanceSource } from "@/lib/api/exam/finance";

// One fixed colour per source, used everywhere on the finance page (legend, chart, tables). The six hues are the first six slots of
// the data-viz categorical palette in the order the sources are listed, so each neighbour pair passes the colour-vision checks. Light
// and dark are the same hues stepped for their surface. The chart's net line is the text colour, not a source colour.

const CSS = `
.finance-viz {
  --fin-src-1: #2a78d6;
  --fin-src-2: #eb6834;
  --fin-src-3: #1baf7a;
  --fin-src-4: #eda100;
  --fin-src-5: #e87ba4;
  --fin-src-6: #008300;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .finance-viz {
    --fin-src-1: #3987e5;
    --fin-src-2: #d95926;
    --fin-src-3: #199e70;
    --fin-src-4: #c98500;
    --fin-src-5: #d55181;
    --fin-src-6: #008300;
  }
}
:root[data-theme="dark"] .finance-viz {
  --fin-src-1: #3987e5;
  --fin-src-2: #d95926;
  --fin-src-3: #199e70;
  --fin-src-4: #c98500;
  --fin-src-5: #d55181;
  --fin-src-6: #008300;
}
`;

/** The colour of each source, as a CSS variable that the theme above sets. */
export const SOURCE_COLOR: Record<FinanceSource, string> = {
  ONLINE_ORDERS: "var(--fin-src-1)",
  DIRECT_ENROLMENT: "var(--fin-src-2)",
  OFFLINE_FEES: "var(--fin-src-3)",
  OTHER_ONLINE: "var(--fin-src-4)",
  STAFF_PAY: "var(--fin-src-5)",
  OFFLINE_INSTRUCTORS: "var(--fin-src-6)",
};

/** Puts the source colours on the page. Render it once, at the top of the finance page. */
export function FinanceTheme() {
  return <style>{CSS}</style>;
}
