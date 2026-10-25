import Link from "next/link";
import { Check, CircleAlert, CircleDashed, Info } from "lucide-react";
import { cn } from "@/components/ui/cn";

export type StepState = "done" | "todo" | "blocked" | "info";

export type StepperStep = {
  key: string;
  label: string;
  state: StepState;
  note?: string;
  href?: string;
};

const ICON: Record<StepState, typeof Check> = {
  done: Check,
  todo: CircleDashed,
  blocked: CircleAlert,
  info: Info,
};

const TONE: Record<StepState, string> = {
  done: "border-ok bg-ok text-on-action",
  todo: "border-border bg-card text-muted",
  blocked: "border-danger bg-card text-danger",
  info: "border-accent bg-card text-text-accent",
};

/** An ordered list of steps, each with its state. Blocked steps keep their link so the user can fix them. */
export function Stepper({
  steps,
  orientation = "vertical",
}: {
  steps: StepperStep[];
  orientation?: "vertical" | "horizontal";
}) {
  const horizontal = orientation === "horizontal";
  return (
    <ol
      data-orientation={orientation}
      className={cn(horizontal ? "flex flex-wrap gap-x-6 gap-y-4" : "grid gap-4")}
    >
      {steps.map((step, index) => {
        const Icon = ICON[step.state];
        const body = (
          <>
            <span className="text-label font-medium text-text">{step.label}</span>
            {step.note ? <span className="text-meta text-muted">{step.note}</span> : null}
          </>
        );
        return (
          <li
            key={step.key}
            data-state={step.state}
            aria-current={step.state === "info" ? "step" : undefined}
            className={cn("flex min-w-0 items-start gap-3", horizontal && "min-w-40")}
          >
            <span
              aria-hidden="true"
              className={cn(
                "inline-flex size-7 shrink-0 items-center justify-center rounded-full border text-pill",
                TONE[step.state],
              )}
            >
              {step.state === "done" ? <Icon className="size-4" /> : <span>{index + 1}</span>}
            </span>
            {step.href ? (
              <Link href={step.href} prefetch className="grid min-w-0 gap-0.5 rounded-control outline-none hover:underline">
                {body}
              </Link>
            ) : (
              <span className="grid min-w-0 gap-0.5">{body}</span>
            )}
          </li>
        );
      })}
    </ol>
  );
}
