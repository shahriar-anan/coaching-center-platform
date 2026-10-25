"use client";

import { useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { IconButton } from "@/components/ui/icon-button";
import { TextInput } from "@/components/ui/text-input";

const DEBOUNCE_MS = 300;

/**
 * A search box. The text shows at once; `onChange` fires 300 ms after typing
 * stops, so a list is not refetched on every key.
 */
export function SearchInput({
  value,
  onChange,
  placeholder,
  id,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  id?: string;
}) {
  const { t } = useLocale();
  const [draft, setDraft] = useState(value);
  const [seen, setSeen] = useState(value);

  // Keep the box in step when the URL changes from outside (Back, another tab).
  if (value !== seen) {
    setSeen(value);
    setDraft(value);
  }

  useEffect(() => {
    if (draft === value) {
      return;
    }
    const id = window.setTimeout(() => onChange(draft), DEBOUNCE_MS);
    return () => window.clearTimeout(id);
  }, [draft, value, onChange]);

  return (
    <div className="relative">
      <Search aria-hidden="true" className="pointer-events-none absolute start-3.5 top-1/2 size-5 -translate-y-1/2 text-muted" />
      <TextInput
        id={id}
        type="search"
        role="searchbox"
        aria-label={t("common.search.label")}
        placeholder={placeholder ?? t("common.search.placeholder")}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        className="ps-11 pe-12 [&::-webkit-search-cancel-button]:hidden"
      />
      {draft ? (
        <div className="absolute inset-y-0 end-0 flex items-center">
          <IconButton
            label={t("common.search.clear")}
            icon={<X aria-hidden="true" className="size-5" />}
            onClick={() => {
              setDraft("");
              onChange("");
            }}
          />
        </div>
      ) : null}
    </div>
  );
}
