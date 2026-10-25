import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";

export type TextInputProps = InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean };

export const TextInput = forwardRef<HTMLInputElement, TextInputProps>(function TextInput(
  { invalid, className, ...rest },
  ref,
) {
  return <input ref={ref} {...rest} className={cn(controlBase, controlBorder(invalid), className)} />;
});
