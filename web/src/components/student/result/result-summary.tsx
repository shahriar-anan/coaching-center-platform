import { CheckCircle2, XCircle } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { Card } from "@/components/ui/card";
import { Pill, type PillTone } from "@/components/ui/pill";

type Verdict = Extract<PillTone, "ok" | "danger" | "muted">;

const fills: Record<Verdict, string> = {
  ok: "bg-ok",
  danger: "bg-danger",
  muted: "bg-muted",
};

/**
 * The headline of a submitted MCQ: the marks as a large figure, the outcome as a pill, and a bar to the full marks with the
 * pass mark ticked on it. Rank and full marks sit beneath.
 */
export function ResultSummary({
  scoreLabel,
  score,
  percent,
  passPercent,
  passLabel,
  outcome,
  verdict,
  rankLabel,
  rank,
  maximumLabel,
  maximum,
}: {
  scoreLabel: string;
  score: string;
  /** The share of full marks, 0 to 100. Null hides the bar. */
  percent: number | null;
  passPercent: number | null;
  passLabel: string | null;
  outcome: string;
  verdict: Verdict;
  rankLabel: string;
  rank: string;
  maximumLabel: string;
  maximum: string;
}) {
  return (
    <Card padding="lg" className="grid gap-5">
      <div className="flex items-start justify-between gap-4">
        <div className="grid min-w-0 gap-2">
          <p className="text-meta text-muted">{scoreLabel}</p>
          <p className="text-4xl font-bold leading-none tabular-nums text-text">{score}</p>
        </div>
        <Pill tone={verdict}>
          {verdict === "ok" ? <CheckCircle2 aria-hidden="true" className="size-3.5" /> : null}
          {verdict === "danger" ? <XCircle aria-hidden="true" className="size-3.5" /> : null}
          {outcome}
        </Pill>
      </div>

      {percent !== null ? (
        <div className="grid gap-2">
          <div className="relative h-2.5 rounded-full bg-border">
            <div
              className={cn("h-full rounded-full transition-[width] duration-500 motion-reduce:transition-none", fills[verdict])}
              style={{ width: `${percent}%` }}
            />
            {passPercent !== null ? (
              <span
                aria-hidden="true"
                className="absolute -inset-y-1 w-0.5 -translate-x-px rounded-full bg-text"
                style={{ left: `${passPercent}%` }}
              />
            ) : null}
          </div>
          {passLabel ? <p className="text-meta text-muted">{passLabel}</p> : null}
        </div>
      ) : null}

      <dl className="grid grid-cols-2 gap-4 border-t border-border pt-4">
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{rankLabel}</dt>
          <dd className="text-section font-bold tabular-nums text-text">{rank}</dd>
        </div>
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{maximumLabel}</dt>
          <dd className="text-section font-bold tabular-nums text-text">{maximum}</dd>
        </div>
      </dl>
    </Card>
  );
}
