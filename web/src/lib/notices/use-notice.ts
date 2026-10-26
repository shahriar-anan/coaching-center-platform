"use client";

import { useEffect } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import { NOTICE_CODES, isNoticeCode } from "@/lib/notices/codes";

/**
 * Shows the toast for a `?notice=<code>` in the URL once, then removes the code so a reload
 * does not repeat it. Other query parameters are kept.
 */
export function useNotice() {
  const { t } = useLocale();
  const { toast } = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  useEffect(() => {
    const code = params.get("notice");
    if (!isNoticeCode(code)) return;
    const notice = NOTICE_CODES[code];
    toast({ title: t(notice.key), tone: notice.tone });
    const rest = new URLSearchParams(params.toString());
    rest.delete("notice");
    const query = rest.toString();
    router.replace(query ? `${pathname}?${query}` : pathname);
  }, [params, pathname, router, t, toast]);
}
