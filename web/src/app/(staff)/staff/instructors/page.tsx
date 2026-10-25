"use client";

import { Suspense, useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { OneTimeCodeDialog } from "@/components/ui/one-time-code-dialog";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { RowMenu } from "@/components/ui/row-menu";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { NewPersonDialog } from "@/components/staff/people/new-person-dialog";
import { accountStatusKey, accountStatusTone } from "@/components/staff/people/people-copy";
import { RequireStaff } from "@/lib/auth/guards";
import {
  useCreateInstructor,
  useInstructors,
  useReissueCode,
  type CreatedAccount,
  type InstructorRow,
} from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function StaffInstructorsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <InstructorList />
      </RequireStaff>
    </Suspense>
  );
}

function InstructorList() {
  const { t } = useLocale();
  const format = useFormat();
  const [params, setParams] = useListParams({ page: 0, size: 20, q: "", status: "", assigned: "" });
  const instructors = useInstructors(params);
  const create = useCreateInstructor();
  const reissue = useReissueCode("instructors");
  const [creating, setCreating] = useState(false);
  const [code, setCode] = useState<{ code: string; name: string } | null>(null);

  const columns: Column<InstructorRow>[] = [
    { key: "name", header: t("staffPeople.instructors.columns.name"), cell: (row) => row.fullName },
    { key: "phone", header: t("staffPeople.instructors.columns.phone"), cell: (row) => <span className="font-latin">{row.phone}</span> },
    { key: "email", header: t("staffPeople.instructors.columns.email"), cell: (row) => <span className="font-latin break-all">{row.email}</span> },
    {
      key: "status",
      header: t("staffPeople.instructors.columns.status"),
      cell: (row) => <Pill tone={accountStatusTone(row.status)}>{t(accountStatusKey(row.status))}</Pill>,
    },
    {
      key: "modules",
      header: t("staffPeople.instructors.columns.modules"),
      cell: (row) =>
        row.modules.length === 0
          ? t("staffPeople.instructors.noModules")
          : row.modules.map((m) => format.pickTitle({ titleEn: m.titleEn, titleBn: m.titleBn })).join("، "),
    },
    {
      key: "actions",
      header: "",
      align: "end",
      cell: (row) =>
        row.status === "PENDING_ACTIVATION" ? (
          <RowMenu
            label={t("staffPeople.instructors.rowMenu.label", { name: row.fullName })}
            items={[
              {
                label: t("staffPeople.instructors.reissue"),
                onClick: () =>
                  reissue.mutate(row.id, {
                    onSuccess: (result) => setCode({ code: result.activationCode, name: row.fullName }),
                  }),
              },
            ]}
          />
        ) : (
          <span className="text-muted">{t("staffResults.result.none")}</span>
        ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPeople.instructors.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffPeople.instructors.title") }]}
        actions={<Button onClick={() => setCreating(true)}>{t("staffPeople.instructors.new")}</Button>}
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label={t("staffPeople.students.searchLabel")}>
          <SearchInput id="instructors-q" value={params.q} onChange={(q) => setParams({ q })} placeholder={t("staffPeople.students.searchPlaceholder")} />
        </Field>
        <Field label={t("staffPeople.instructors.filterStatus")}>
          <Select value={params.status} onChange={(event) => setParams({ status: event.target.value })}>
            <option value="">{t("staffPeople.instructors.filters.allStatus")}</option>
            <option value="ACTIVE">{t("staffPeople.status.ACTIVE")}</option>
            <option value="PENDING_ACTIVATION">{t("staffPeople.status.PENDING_ACTIVATION")}</option>
            <option value="INACTIVE">{t("staffPeople.status.INACTIVE")}</option>
          </Select>
        </Field>
        <Field label={t("staffPeople.instructors.filterAssigned")}>
          <Select value={params.assigned} onChange={(event) => setParams({ assigned: event.target.value })}>
            <option value="">{t("staffPeople.instructors.filters.allAssigned")}</option>
            <option value="true">{t("staffPeople.instructors.filters.assigned")}</option>
            <option value="false">{t("staffPeople.instructors.filters.unassigned")}</option>
          </Select>
        </Field>
      </div>

      {instructors.isError ? (
        <ErrorState onRetry={() => instructors.refetch()} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={instructors.data?.content ?? []}
            rowKey={(row) => row.id}
            loading={instructors.isPending}
            empty={<EmptyState title={t("staffPeople.instructors.empty")} body={t("staffPeople.instructors.emptyBody")} />}
          />
          {instructors.data ? (
            <Pagination page={instructors.data.page} totalPages={instructors.data.totalPages} onPage={(page) => setParams({ page })} />
          ) : null}
        </>
      )}

      <NewPersonDialog
        open={creating}
        onOpenChange={setCreating}
        title={t("staffPeople.instructors.createTitle")}
        submit={(body) => create.mutateAsync(body)}
        onCreated={(account: CreatedAccount) => setCode({ code: account.activationCode, name: account.fullName })}
      />
      <OneTimeCodeDialog open={code !== null} code={code?.code ?? ""} personName={code?.name ?? ""} onClose={() => setCode(null)} />
    </div>
  );
}
