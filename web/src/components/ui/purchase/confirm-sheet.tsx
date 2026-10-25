import type { ReactNode } from "react";

/** The purchase confirmation. Stub until P-3 lands: renders its content inline when open. */
export function ConfirmSheet({
  open,
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
  if (!open) return null;
  return (
    <div role="dialog" aria-label={title}>
      <p>{amountLabel}</p>
      <p>{unlock}</p>
      {children}
      {primary}
      <button type="button">{cancelLabel}</button>
      {leavingNote ? <p>{leavingNote}</p> : null}
    </div>
  );
}
