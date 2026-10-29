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
      // The browser's spin buttons drew a tall box beside the number; the field keeps the same shape as a text field.
      className={cn(
        controlBase,
        controlBorder(invalid),
        "tabular-nums [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none",
        className,
      )}
    />
  );
});
