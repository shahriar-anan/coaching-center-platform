"use client";

import { forwardRef, useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";
import type { TextInputProps } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";

/** A password field with a show and hide button that is named for screen readers. */
export const PasswordInput = forwardRef<HTMLInputElement, Omit<TextInputProps, "type">>(
  function PasswordInput({ invalid, className, ...rest }, ref) {
    const { t } = useLocale();
    const [shown, setShown] = useState(false);
    return (
      <div className="relative">
        <input
          ref={ref}
          {...rest}
          type={shown ? "text" : "password"}
          autoComplete={rest.autoComplete ?? "current-password"}
          className={cn(controlBase, controlBorder(invalid), "pe-12", className)}
        />
        <button
          type="button"
          onClick={() => setShown((v) => !v)}
          aria-label={shown ? t("common.actions.hidePassword") : t("common.actions.showPassword")}
          aria-pressed={shown}
          className="absolute inset-y-0 end-0 flex w-11 items-center justify-center text-muted hover:text-text"
        >
          {shown ? (
            <EyeOff aria-hidden="true" className="size-5" />
          ) : (
            <Eye aria-hidden="true" className="size-5" />
          )}
        </button>
      </div>
    );
  },
);
