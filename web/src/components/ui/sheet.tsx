"use client";

import type { ReactNode } from "react";
import { Dialog } from "@/components/ui/dialog";

/** A bottom sheet on phones and a dialog on desktop. Same focus rules as `Dialog`. */
export function Sheet({
  open,
  onOpenChange,
  title,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title}>
      {children}
    </Dialog>
  );
}
