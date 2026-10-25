import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { LocaleProvider } from "@/i18n/locale-context";

/** Temporary until WS0's renderWithProviders lands: query client plus locale. */
export function renderWithQuery(ui: ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <LocaleProvider>{ui}</LocaleProvider>
    </QueryClientProvider>,
  );
}
