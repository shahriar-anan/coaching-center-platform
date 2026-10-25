// Stand-in for PUR-FND's ConfirmSheet. Delete at the switch to @/components/ui/purchase.
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";

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
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title}>
      <div className="grid gap-3">
        <p className="text-card font-bold">{amountLabel}</p>
        <p className="text-body text-muted">{unlock}</p>
        {children}
        {primary}
        <Button variant="secondary" onClick={() => onOpenChange(false)}>
          {cancelLabel}
        </Button>
        {leavingNote ? <p className="text-meta text-muted">{leavingNote}</p> : null}
      </div>
    </Dialog>
  );
}
