"use client";

import { useRef, type ReactNode } from "react";
import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

/**
 * The purchase confirmation. On phones it slides up from the bottom; on desktop
 * it is a centred dialog (both from `Dialog`). Focus goes to the primary button
 * on open and back to the trigger on close; Escape closes it.
 */
export function ConfirmSheet({
  open,
  onOpenChange,
  title,
  amountLabel,
  unlock,
  primary,
  cancelLabel,
  leavingNote,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  amountLabel: string;
  unlock: string;
  primary: ReactNode;
  cancelLabel: string;
  leavingNote?: string;
  children?: ReactNode;
}) {
  const footerRef = useRef<HTMLDivElement>(null);
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      initialFocus={() => footerRef.current?.querySelector<HTMLElement>("[data-primary] button, [data-primary] a[href]") ?? null}
    >
      <div className="grid gap-3">
        <p className="text-card font-semibold tabular-nums text-text">{amountLabel}</p>
        <p className="text-body text-muted">{unlock}</p>
        {children}
        <div ref={footerRef} className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={() => onOpenChange(false)} className="w-full sm:w-auto">
            {cancelLabel}
          </Button>
          <div data-primary="" className="w-full sm:w-auto">{primary}</div>
        </div>
        {leavingNote ? <p className="text-meta text-muted">{leavingNote}</p> : null}
      </div>
    </Dialog>
  );
}
