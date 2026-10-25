import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/components/ui/cn";

/** A checkbox with a 44 px target. Pair it with a visible label. */
export const Checkbox = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & { invalid?: boolean }
>(function Checkbox({ invalid, className, ...rest }, ref) {
  return (
    <input
      ref={ref}
      type="checkbox"
      {...rest}
      className={cn(
        "m-2.5 size-6 shrink-0 accent-action",
        invalid && "outline-2 outline-danger",
        className,
      )}
    />
  );
});
