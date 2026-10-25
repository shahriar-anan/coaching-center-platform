"use client";

import { Suspense, use, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { accessReasonKey, accountStatusKey, accountStatusTone } from "@/components/staff/people/people-copy";
import { RequireStaff, useRole } from "@/lib/auth/guards";
import {
  useStudent,
  useStudentAccess,
  useSetStatus,
  useUpdateStudentName,
  type AccessRow,
} from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
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
  const { isMasterAdmin } = useRole();
  const student = useStudent(id);
  const access = useStudentAccess(id, 0);

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
              <dt className="text-sm text-muted">{t("staffPeople.students.columns.status")}</dt>
              <dd>
                <Pill tone={accountStatusTone(row.status)}>{t(accountStatusKey(row.status))}</Pill>
              </dd>
            </div>
          </dl>
          {row.status !== "PENDING_ACTIVATION" ? <StatusToggle id={id} status={row.status} /> : null}
        </Card>
      </div>

      <section className="grid gap-3">
        <h2 className="text-lg font-semibold text-text">{t("staffPeople.student.accessTitle")}</h2>
        {access.isError ? (
          <ErrorState onRetry={() => access.refetch()} />
        ) : (
          <AccessTable rows={access.data?.content ?? []} loading={access.isPending} canRoster={isMasterAdmin} />
        )}
      </section>
    </div>
  );
}

function Fact({ label, value, latin }: { label: string; value: string; latin?: boolean }) {
  return (
    <div className="grid gap-1">
      <dt className="text-sm text-muted">{label}</dt>
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

function StatusToggle({ id, status }: { id: string; status: "ACTIVE" | "INACTIVE" }) {
  const { t } = useLocale();
  const setStatus = useSetStatus("students");
  const next = status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  return (
    <div className="mt-4">
      <Button variant="secondary" loading={setStatus.isPending} onClick={() => setStatus.mutate({ id, status: next })}>
        {next === "INACTIVE" ? t("staffPeople.students.deactivate") : t("staffPeople.students.activate")}
      </Button>
    </div>
  );
}

/** Each module or exam the student can sit, and why. Only Master Admin gets the roster link. */
function AccessTable({ rows, loading, canRoster }: { rows: AccessRow[]; loading: boolean; canRoster: boolean }) {
  const { t } = useLocale();
  const format = useFormat();

  const columns: Column<AccessRow>[] = [
    {
      key: "module",
      header: t("staffPeople.student.columns.module"),
      cell: (row) => (
        <a href={`/staff/modules/${row.moduleId}`} className="font-medium text-text underline-offset-4 hover:underline">
          {format.pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn })}
        </a>
      ),
    },
    {
      key: "reason",
      header: t("staffPeople.student.columns.reason"),
      cell: (row) => <Pill tone={row.via === "ROSTER" ? "accent" : "ok"}>{t(accessReasonKey(row.via))}</Pill>,
    },
    {
      key: "scope",
      header: t("staffPeople.student.columns.scope"),
      cell: (row) =>
        row.via === "EXAM_PURCHASE"
          ? row.exams.map((exam) => format.pickTitle({ titleEn: exam.titleEn, titleBn: exam.titleBn })).join("، ")
          : t("staffPeople.student.wholeModule"),
    },
    {
      key: "links",
      header: "",
      align: "end",
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          {row.tranId ? (
            <a
              href={`/staff/payments/${encodeURIComponent(row.tranId)}`}
              className="text-sm text-action underline-offset-4 hover:underline"
            >
              {t("staffPeople.student.openPayment")}
            </a>
          ) : null}
          {canRoster ? (
            <a
              href={`/staff/modules/${row.moduleId}?tab=roster`}
              className="text-sm text-action underline-offset-4 hover:underline"
            >
              {t("staffPeople.student.addToRoster")}
            </a>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      rows={rows}
      rowKey={(row) => `${row.moduleId}-${row.via}-${row.orderId ?? "roster"}`}
      loading={loading}
      empty={<EmptyState title={t("staffPeople.student.accessEmpty")} />}
    />
  );
}
