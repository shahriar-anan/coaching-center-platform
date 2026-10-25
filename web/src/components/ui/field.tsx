"use client";

import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";

type FieldProps = {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: ReactElement<{ id?: string; "aria-describedby"?: string; "aria-invalid"?: boolean; "aria-required"?: boolean }>;
};

/**
 * One label, one hint and one error around a single input. The label is always
 * visible; the error sits under the field that failed.
 */
export function Field({ label, hint, error, required, children }: FieldProps) {
  const { t } = useLocale();
  const reactId = useId();
  const id = (isValidElement(children) && children.props.id) || `f-${reactId}`;
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  const control = isValidElement(children)
    ? cloneElement(children, {
        id,
        "aria-describedby": describedBy,
        "aria-invalid": error ? true : undefined,
        "aria-required": required || undefined,
      })
    : children;

  return (
    <div className="grid gap-1.5">
      <label htmlFor={id} className="text-label font-medium text-text">
        {label}
        {required ? (
          <span className="ms-1 text-muted" aria-hidden="true">
            *
          </span>
        ) : null}
        {required ? <span className="sr-only"> ({t("common.states.required")})</span> : null}
      </label>
      {control as ReactNode}
      {hint && !error ? (
        <p id={hintId} className="text-meta text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-meta text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
