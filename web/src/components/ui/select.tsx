import { forwardRef, type SelectHTMLAttributes } from "react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";

export const Select = forwardRef<
  HTMLSelectElement,
  SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }
>(function Select({ invalid, className, children, ...rest }, ref) {
  return (
    <select ref={ref} {...rest} className={cn(controlBase, "pe-9", controlBorder(invalid), className)}>
      {children}
    </select>
  );
});
