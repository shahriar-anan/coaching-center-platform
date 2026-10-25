"use client";

import { Suspense, useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { OneTimeCodeDialog } from "@/components/ui/one-time-code-dialog";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { NewPersonDialog } from "@/components/staff/people/new-person-dialog";
import { accountStatusKey, accountStatusTone } from "@/components/staff/people/people-copy";
import { RequireStaff } from "@/lib/auth/guards";
import {
  useAdmins,
  useCreateAdmin,
  useDeletePerson,
  useReissueCode,
  useSetStatus,
  type CreatedAccount,
  type StudentRow,
} from "@/lib/api/exam/staff-people";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function StaffAdminsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <AdminList />
      </RequireStaff>
    </Suspense>
  );
}

/** The system admin list. Short and paged, with no search. The Master Admin is never a row. */
function AdminList() {
  const { t } = useLocale();
  const [params, setParams] = useListParams({ page: 0 });
  const admins = useAdmins(params.page);
  const create = useCreateAdmin();
  const setStatus = useSetStatus("admins");
  const reissue = useReissueCode("admins");
  const remove = useDeletePerson("admins");
  const [creating, setCreating] = useState(false);
  const [code, setCode] = useState<{ code: string; name: string } | null>(null);
  const [deleting, setDeleting] = useState<StudentRow | null>(null);

  const columns: Column<StudentRow>[] = [
    { key: "name", header: t("staffPeople.admins.columns.name"), cell: (row) => row.fullName },
    { key: "phone", header: t("staffPeople.admins.columns.phone"), cell: (row) => <span className="font-latin">{row.phone}</span> },
    { key: "email", header: t("staffPeople.admins.columns.email"), cell: (row) => <span className="font-latin break-all">{row.email}</span> },
    {
      key: "status",
      header: t("staffPeople.admins.columns.status"),
      cell: (row) => <Pill tone={accountStatusTone(row.status)}>{t(accountStatusKey(row.status))}</Pill>,
    },
    {
      key: "actions",
      header: "",
      align: "end",
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          {row.status === "PENDING_ACTIVATION" ? (
            <Button
              variant="secondary"
              loading={reissue.isPending && reissue.variables === row.id}
              onClick={async () => {
                const result = await reissue.mutateAsync(row.id);
                setCode({ code: result.activationCode, name: row.fullName });
              }}
            >
              {t("staffPeople.admins.reissue")}
            </Button>
          ) : (
            <Button
              variant="secondary"
              loading={setStatus.isPending && setStatus.variables?.id === row.id}
              onClick={() => setStatus.mutate({ id: row.id, status: row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" })}
            >
              {row.status === "ACTIVE" ? t("staffPeople.students.deactivate") : t("staffPeople.students.activate")}
            </Button>
          )}
          <Button variant="ghost" onClick={() => setDeleting(row)}>
            {t("staffPeople.admins.delete")}
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPeople.admins.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffPeople.admins.title") }]}
        actions={<Button onClick={() => setCreating(true)}>{t("staffPeople.admins.new")}</Button>}
      />

      {admins.isError ? (
        <ErrorState onRetry={() => admins.refetch()} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={admins.data?.content ?? []}
            rowKey={(row) => row.id}
            loading={admins.isPending}
            empty={<EmptyState title={t("staffPeople.admins.empty")} body={t("staffPeople.admins.emptyBody")} />}
          />
          {admins.data ? (
            <Pagination page={admins.data.page} totalPages={admins.data.totalPages} onPage={(page) => setParams({ page })} />
          ) : null}
        </>
      )}

      <NewPersonDialog
        open={creating}
        onOpenChange={setCreating}
        title={t("staffPeople.admins.createTitle")}
        submit={(body) => create.mutateAsync(body)}
        onCreated={(account: CreatedAccount) => setCode({ code: account.activationCode, name: account.fullName })}
      />
      <OneTimeCodeDialog open={code !== null} code={code?.code ?? ""} personName={code?.name ?? ""} onClose={() => setCode(null)} />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("staffPeople.admins.deleteTitle", { name: deleting?.fullName ?? "" })}
        body={t("staffPeople.admins.deleteBody")}
        confirmLabel={t("staffPeople.admins.deleteConfirm")}
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
