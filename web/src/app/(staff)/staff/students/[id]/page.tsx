"use client";

import { Suspense, use, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { StudentAccessSection } from "@/components/staff/access/student-access-section";
import { accountStatusKey, accountStatusTone } from "@/components/staff/people/people-copy";
import { StudentExamHistory } from "@/components/staff/results/student-exam-history";
import { AccountStatusToggle, DeleteAccountButton } from "@/components/staff/status/account-controls";
import { RequireStaff } from "@/lib/auth/guards";
import { useStudent, useUpdateStudentName } from "@/lib/api/exam/staff-people";
import { useLocale } from "@/i18n/locale-context";

export default function StaffStudentPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <StudentRoute params={params} />
    </Suspense>
  );
}

function StudentRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <StudentDetail id={id} />
    </RequireStaff>
  );
}

function StudentDetail({ id }: { id: string }) {
  const { t } = useLocale();
  const router = useRouter();
  const student = useStudent(id);

  if (student.isError) {
    return <ErrorState onRetry={() => student.refetch()} />;
  }
  if (student.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (!student.data) {
    return <EmptyState title={t("common.notFound.title")} />;
  }
  const row = student.data;

  return (
    <div className="grid gap-5">
      <PageHeader
        title={row.fullName}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffPeople.students.title"), href: "/staff/students" },
          { label: row.fullName },
        ]}
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <NameCard id={id} name={row.fullName} />
        <Card>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Fact label={t("staffPeople.students.columns.phone")} value={row.phone} latin />
            <Fact label={t("staffPeople.students.columns.email")} value={row.email} latin />
            <Fact label={t("staffPeople.students.columns.code")} value={row.studentCode ?? "—"} latin />
            <div className="grid gap-1">
              <dt className="text-meta text-muted">{t("staffPeople.students.columns.status")}</dt>
              <dd>
                <Pill tone={accountStatusTone(row.status)}>{t(accountStatusKey(row.status))}</Pill>
              </dd>
            </div>
          </dl>
          <div className="mt-4 flex flex-wrap gap-2 empty:hidden">
            <AccountStatusToggle kind="students" id={id} status={row.status} />
            <DeleteAccountButton
              kind="students"
              id={id}
              name={row.fullName}
              onDeleted={() => router.push("/staff/students")}
            />
          </div>
        </Card>
      </div>

      <StudentAccessSection studentId={id} studentName={row.fullName} />

      <section className="grid gap-3">
        <h2 className="text-section text-text">{t("staffResults.history.title")}</h2>
        <StudentExamHistory studentId={id} />
      </section>
    </div>
  );
}

function Fact({ label, value, latin }: { label: string; value: string; latin?: boolean }) {
  return (
    <div className="grid gap-1">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className={latin ? "font-latin break-all text-text" : "text-text"}>{value}</dd>
    </div>
  );
}

/** The name is editable for Master Admin and System Admin. */
function NameCard({ id, name }: { id: string; name: string }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const update = useUpdateStudentName();
  const [value, setValue] = useState(name);
  const [error, setError] = useState<string | undefined>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!value.trim()) {
      setError(t("staffPeople.form.required"));
      return;
    }
    setError(undefined);
    try {
      await update.mutateAsync({ id, fullName: value.trim() });
      toast({ title: t("staffPeople.student.nameSaved"), tone: "ok" });
    } catch {
      setError(t("common.error.body"));
    }
  }

  return (
    <Card>
      <form className="grid gap-4" onSubmit={submit} noValidate>
        <Field label={t("staffPeople.form.name")} error={error} required>
          <TextInput value={value} onChange={(event) => setValue(event.target.value)} invalid={Boolean(error)} />
        </Field>
        <div>
          <Button type="submit" loading={update.isPending}>
            {t("common.actions.save")}
          </Button>
        </div>
      </form>
    </Card>
  );
}

