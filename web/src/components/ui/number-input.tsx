import { forwardRef } from "react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";
import type { TextInputProps } from "@/components/ui/text-input";

export const NumberInput = forwardRef<HTMLInputElement, TextInputProps>(function NumberInput(
  { invalid, className, ...rest },
  ref,
) {
  return (
    <input
      ref={ref}
      inputMode="numeric"
      {...rest}
      type="number"
      className={cn(controlBase, controlBorder(invalid), "tabular-nums", className)}
    />
  );
});
