import type { UseQueryResult } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ResultsSection } from "@/components/student/my-admission/results-section";
import type { MyAdmissionResults } from "@/lib/api/exam/my-results";
import { fixtures, renderWithProviders } from "../helpers";
import { admissionResults, examResult } from "./fixture";

/** A query result as the section reads it: loaded, or still pending. */
function loaded(): UseQueryResult<MyAdmissionResults> {
  return { isPending: false, isError: false, data: admissionResults([examResult()]), error: null } as unknown as UseQueryResult<MyAdmissionResults>;
}
function pending(): UseQueryResult<MyAdmissionResults> {
  return { isPending: true, isError: false, data: undefined, error: null } as unknown as UseQueryResult<MyAdmissionResults>;
}

/** Two admissions on one page, each with its own results section. */
function Two({ first, second }: { first: UseQueryResult<MyAdmissionResults>; second: UseQueryResult<MyAdmissionResults> }) {
  return (
    <>
      <ResultsSection admissionId="adm-1" results={first} />
      <ResultsSection admissionId="adm-2" results={second} />
    </>
  );
}

const OPTIONS = { user: fixtures.student, locale: "en" as const, route: "/offline-activities" };

describe("each results section has its own id", () => {
  it("gives two admissions two different ids", () => {
    renderWithProviders(<Two first={loaded()} second={loaded()} />, OPTIONS);

    const one = document.getElementById("results-adm-1");
    const two = document.getElementById("results-adm-2");
    expect(one).not.toBeNull();
    expect(two).not.toBeNull();
    expect(one).not.toBe(two);
    expect(document.querySelectorAll('[id="results"]')).toHaveLength(0);
  });
});

describe("the notice link's jump to the results (#results)", () => {
  let scrolled: string[];

  beforeEach(() => {
    scrolled = [];
    Element.prototype.scrollIntoView = function scrollIntoView(this: Element) {
      scrolled.push(this.id);
    };
    window.history.replaceState(null, "", "/offline-activities#results");
  });

  afterEach(() => {
    delete (Element.prototype as { scrollIntoView?: unknown }).scrollIntoView;
    window.history.replaceState(null, "", "/");
  });

  it("scrolls the first section into view once its data has arrived, then clears the hash", () => {
    // The second admission's results arrive first: the jump waits for the first section.
    const { rerender } = renderWithProviders(<Two first={pending()} second={loaded()} />, OPTIONS);
    expect(scrolled).toEqual([]);
    expect(window.location.hash).toBe("#results");

    rerender(<Two first={loaded()} second={loaded()} />);

    expect(scrolled).toEqual(["results-adm-1"]);
    expect(window.location.hash).toBe("");
  });

  it("does not scroll when the address has no results hash", () => {
    window.history.replaceState(null, "", "/offline-activities");

    renderWithProviders(<Two first={loaded()} second={loaded()} />, OPTIONS);

    expect(scrolled).toEqual([]);
  });
});
