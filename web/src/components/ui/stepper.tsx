import type { ReactNode } from "react";

export type StepState = "done" | "todo" | "blocked" | "info";

export type StepperStep = {
  key: string;
  label: string;
  state: StepState;
  note?: string;
  href?: string;
};

/** Ordered steps with a state each. Stub: renders the labels in order. */
export function Stepper({
  steps,
  orientation = "vertical",
}: {
  steps: StepperStep[];
  orientation?: "vertical" | "horizontal";
}): ReactNode {
  return (
    <ol data-orientation={orientation} className="grid gap-2">
      {steps.map((step) => (
        <li key={step.key} data-state={step.state}>
          {step.label}
        </li>
      ))}
    </ol>
  );
}
