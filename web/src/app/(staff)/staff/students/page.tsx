"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { OneTimeCodeDialog } from "@/components/ui/one-time-code-dialog";
import { Pagination } from "@/components/ui/pagination";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { RowMenu } from "@/components/ui/row-menu";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { NewPersonDialog } from "@/components/staff/people/new-person-dialog";
import { accountStatusKey, accountStatusTone } from "@/components/staff/people/people-copy";
import { RequireStaff, useRole } from "@/lib/auth/guards";
import {
  useCreateStudent,
  useDeletePerson,
  useReissueCode,
  useSetStatus,
  useStudents,
  type CreatedAccount,
  type StudentRow,
} from "@/lib/api/exam/staff-people";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function StaffStudentsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <StudentList />
      </RequireStaff>
    </Suspense>
  );
}

function StudentList() {
  const { t } = useLocale();
  const { isMasterAdmin } = useRole();
  const [params, setParams] = useListParams({ page: 0, size: 20, q: "" });
  const students = useStudents(params);
  const create = useCreateStudent();
  const setStatus = useSetStatus("students");
  const reissue = useReissueCode("students");
  const remove = useDeletePerson("students");

  const [creating, setCreating] = useState(false);
  const [code, setCode] = useState<{ code: string; name: string } | null>(null);
  const [deleting, setDeleting] = useState<StudentRow | null>(null);

  const columns: Column<StudentRow>[] = [
    {
      key: "name",
      header: t("staffPeople.students.columns.name"),
      cell: (row) => (
        <Link href={`/staff/students/${row.id}`} className="font-medium text-text underline-offset-4 hover:underline">
          {row.fullName}
        </Link>
      ),
    },
    { key: "phone", header: t("staffPeople.students.columns.phone"), cell: (row) => <span className="font-latin">{row.phone}</span> },
    { key: "email", header: t("staffPeople.students.columns.email"), cell: (row) => <span className="font-latin break-all">{row.email}</span> },
    { key: "code", header: t("staffPeople.students.columns.code"), cell: (row) => <span className="font-latin">{row.studentCode ?? "—"}</span> },
    {
      key: "status",
      header: t("staffPeople.students.columns.status"),
      cell: (row) => <Pill tone={accountStatusTone(row.status)}>{t(accountStatusKey(row.status))}</Pill>,
    },
    {
      key: "actions",
      header: "",
      align: "end",
      cell: (row) => (
        <RowMenu
          label={t("staffPeople.students.rowMenu.label", { name: row.fullName })}
          items={[
            { label: t("staffPeople.students.rowMenu.open"), href: `/staff/students/${row.id}` },
            row.status === "PENDING_ACTIVATION"
              ? {
                  label: t("staffPeople.students.reissue"),
                  onClick: () =>
                    reissue.mutate(row.id, {
                      onSuccess: (result) => setCode({ code: result.activationCode, name: row.fullName }),
                    }),
                }
              : {
                  label: row.status === "ACTIVE" ? t("staffPeople.students.deactivate") : t("staffPeople.students.activate"),
                  onClick: () =>
                    setStatus.mutate({ id: row.id, status: row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" }),
                },
            ...(isMasterAdmin
              ? [{ label: t("staffPeople.students.delete"), tone: "danger" as const, onClick: () => setDeleting(row) }]
              : []),
          ]}
        />
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPeople.students.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffPeople.students.title") }]}
        actions={<Button onClick={() => setCreating(true)}>{t("staffPeople.students.new")}</Button>}
      />
      <SearchInput
        id="students-q"
        value={params.q}
        onChange={(q) => setParams({ q })}
        placeholder={t("staffPeople.students.searchPlaceholder")}
      />

      {students.isError ? (
        <ErrorState onRetry={() => students.refetch()} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={students.data?.content ?? []}
            rowKey={(row) => row.id}
            loading={students.isPending}
            empty={<EmptyState title={t("staffPeople.students.empty")} body={t("staffPeople.students.emptyBody")} />}
          />
          {students.data ? (
            <Pagination page={students.data.page} totalPages={students.data.totalPages} onPage={(page) => setParams({ page })} />
          ) : null}
        </>
      )}

      <NewPersonDialog
        open={creating}
        onOpenChange={setCreating}
        title={t("staffPeople.students.createTitle")}
        submit={(body) => create.mutateAsync(body)}
        onCreated={(account: CreatedAccount) => setCode({ code: account.activationCode, name: account.fullName })}
      />

      <OneTimeCodeDialog
        open={code !== null}
        code={code?.code ?? ""}
        personName={code?.name ?? ""}
        onClose={() => setCode(null)}
      />

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("staffPeople.students.deleteTitle", { name: deleting?.fullName ?? "" })}
        body={t("staffPeople.students.deleteBody")}
        confirmLabel={t("staffPeople.students.deleteConfirm")}
        tone="danger"
        typeToConfirm={deleting?.fullName ?? ""}
        onConfirm={async () => {
          if (deleting) {
            await remove.mutateAsync(deleting.id);
          }
          setDeleting(null);
        }}
      />
    </div>
  );
}
