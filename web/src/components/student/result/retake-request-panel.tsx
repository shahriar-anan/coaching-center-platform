"use client";

import { useState } from "react";
import { Clock, Mail, MapPin, Phone } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { formatDateTime } from "@/lib/format";
import { useMyRetakeRequest, useRequestRetake } from "@/lib/api/exam/retake-requests";

const MAX_REASON = 500;

/**
 * The student's retake request on a written exam: ask for one, see that it is waiting, or read the centre's note when it was
 * declined. Each state carries Symam Law's contact details. The panel is hidden when no request can be made.
 */
export function RetakeRequestPanel({ examId, enabled }: { examId: string; enabled: boolean }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const state = useMyRetakeRequest(examId, enabled);
  const send = useRequestRetake(examId);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [failed, setFailed] = useState(false);

  const data = state.data;
  if (!enabled || !data) return null;
  const request = data.request;
  const pending = request?.status === "PENDING";
  const declined = request?.status === "DECLINED";
  if (!pending && !data.canRequest) return null;

  async function submit() {
    setFailed(false);
    try {
      await send.mutateAsync(reason);
      setOpen(false);
      setReason("");
      toast({ title: t("result.retake.sent"), tone: "ok" });
    } catch {
      setFailed(true);
    }
  }

  return (
    <Card className="grid gap-4">
      <div className="grid gap-1">
        <h2 className="text-label font-semibold text-text">{t("result.retake.title")}</h2>
        <p className="text-body text-muted">
          {pending ? t("result.retake.pending") : t("result.retake.body")}
        </p>
      </div>

      {declined && request?.decisionNote ? (
        <div className="grid gap-1 rounded-control border border-warn/40 bg-warn/10 px-4 py-3">
          <p className="text-meta font-semibold text-warn">{t("result.retake.declined")}</p>
          <p className="whitespace-pre-line text-body text-text">{request.decisionNote}</p>
        </div>
      ) : declined ? (
        <p className="text-meta font-semibold text-warn">{t("result.retake.declined")}</p>
      ) : null}

      {pending && request ? (
        <p className="text-meta text-muted">
          {t("result.retake.sentAt", { time: formatDateTime(request.requestedAt, locale) })}
        </p>
      ) : data.canRequest ? (
        <div>
          <Button variant="secondary" onClick={() => setOpen(true)}>
            {t("result.retake.request")}
          </Button>
        </div>
      ) : null}

      <ContactBlock />

      <Dialog
        open={open}
        onOpenChange={setOpen}
        title={t("result.retake.dialogTitle")}
        description={t("result.retake.dialogBody")}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={send.isPending}>
              {t("common.actions.cancel")}
            </Button>
            <Button loading={send.isPending} disabled={send.isPending} onClick={() => void submit()}>
              {t("result.retake.send")}
            </Button>
          </>
        }
      >
        <div className="grid gap-3">
          <Field label={t("result.retake.reasonLabel")} hint={t("result.retake.reasonHint")}>
            <Textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={4} maxLength={MAX_REASON} />
          </Field>
          {failed ? (
            <p role="alert" className="text-meta text-danger">
              {t("result.retake.failed")}
            </p>
          ) : null}
        </div>
      </Dialog>
    </Card>
  );
}

/** Symam Law's contact details, from the same copy as the site footer. */
function ContactBlock() {
  const { t } = useLocale();
  const rows = [
    { icon: Phone, value: t("common.footer.phone") },
    { icon: Mail, value: t("common.footer.email") },
    { icon: Clock, value: t("common.footer.hours") },
    { icon: MapPin, value: t("common.footer.location") },
  ];
  return (
    <div className="grid gap-2 border-t border-border pt-4">
      <p className="text-meta font-semibold uppercase tracking-wide text-muted">{t("result.retake.contactHeading")}</p>
      <ul className="grid gap-1.5 text-meta text-text">
        {rows.map(({ icon: Icon, value }) => (
          <li key={value} className="flex items-start gap-2">
            <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted" />
            <span className="min-w-0 break-words">{value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

