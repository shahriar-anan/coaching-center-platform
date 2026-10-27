"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useListParams } from "@/lib/url/list-params";
import {
  useAssignInstructor,
  useInstructorDirectory,
  useModule,
  useUnassignInstructor,
  type InstructorRow,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { Select } from "@/components/ui/select";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { Pill } from "@/components/ui/pill";
import { catalogErrorKey, isApiError } from "@/components/staff/catalog/errors";

const STATUS_TONE = { ACTIVE: "ok", PENDING_ACTIVATION: "warn", INACTIVE: "muted" } as const;

/** The directory as a picker. The Master Admin chooses one person for this module. */
function InstructorPicker({ moduleId, open, onOpenChange }: { moduleId: string; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const [params, setParams] = useListParams({ q: "", status: "", assigned: "", page: 0 });
  const directory = useInstructorDirectory({
    q: params.q,
    status: params.status,
    assigned: params.assigned === "true" || params.assigned === "false" ? params.assigned : "",
    page: params.page,
  });
  const assign = useAssignInstructor(moduleId);
  const [error, setError] = useState<string | null>(null);

  async function choose(row: InstructorRow) {
    setError(null);
    try {
      await assign.mutateAsync(row.id);
      toast({ title: t("staffCatalog.instructor.assigned"), tone: "ok" });
      onOpenChange(false);
    } catch (e) {
      setError(isApiError(e, "INSTRUCTOR_ALREADY_ASSIGNED") ? t("staffCatalog.instructor.alreadyAssigned") : t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.instructor.pickerTitle")}
      description={t("staffCatalog.instructor.pickerBody")}
      className="sm:max-w-2xl"
    >
      <div className="grid gap-3">
        <SearchInput value={params.q} onChange={(q) => setParams({ q, page: 0 })} placeholder={t("staffCatalog.instructor.searchPlaceholder")} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Select aria-label={t("staffCatalog.instructor.filterStatus")} value={params.status} onChange={(e) => setParams({ status: e.target.value, page: 0 })}>
            <option value="">{t("staffCatalog.instructor.allStatuses")}</option>
            <option value="ACTIVE">{t("staffCatalog.instructorStatus.ACTIVE")}</option>
            <option value="PENDING_ACTIVATION">{t("staffCatalog.instructorStatus.PENDING_ACTIVATION")}</option>
            <option value="INACTIVE">{t("staffCatalog.instructorStatus.INACTIVE")}</option>
          </Select>
          <Select aria-label={t("staffCatalog.instructor.filterAssigned")} value={params.assigned} onChange={(e) => setParams({ assigned: e.target.value, page: 0 })}>
            <option value="">{t("staffCatalog.instructor.allAssigned")}</option>
            <option value="true">{t("staffCatalog.instructor.assignedOnly")}</option>
            <option value="false">{t("staffCatalog.instructor.unassignedOnly")}</option>
          </Select>
        </div>
        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
        {directory.isError && !directory.data ? (
          <ErrorState onRetry={() => directory.refetch()} message={t("staffCatalog.instructor.loadError")} />
        ) : (
          <>
            {directory.isPending ? <Skeleton variant="card" count={2} /> : null}
            {!directory.isPending && (directory.data?.content ?? []).length === 0 ? (
              <EmptyState title={t("staffCatalog.instructor.emptyDirectory")} />
            ) : null}
            <ul className="grid gap-3">
              {(directory.data?.content ?? []).map((r) => (
                <li key={r.id} className="grid gap-3 rounded-card border border-border bg-card p-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="grid min-w-0 gap-0.5">
                      <p className="font-semibold break-words">{r.fullName ?? "—"}</p>
                      <p dir="ltr" className="text-meta text-muted tabular-nums">
                        {r.phone ?? "—"}
                      </p>
                    </div>
                    <Pill tone={STATUS_TONE[r.status]}>{t(`staffCatalog.instructorStatus.${r.status}`)}</Pill>
                  </div>
                  <p className="text-meta">
                    <span className="font-medium text-muted">{t("staffCatalog.instructor.columns.modules")}: </span>
                    {r.modules.length === 0 ? t("staffCatalog.instructor.noModules") : r.modules.map((m) => m.titleBn).join("، ")}
                  </p>
                  <Button
                    variant="secondary"
                    className="min-h-11 w-full sm:w-auto sm:justify-self-end"
                    disabled={r.status !== "ACTIVE" || assign.isPending}
                    onClick={() => choose(r)}
                  >
                    {t("staffCatalog.instructor.choose")}
                  </Button>
                </li>
              ))}
            </ul>
            <Pagination page={params.page} totalPages={directory.data?.totalPages ?? 0} onPage={(page) => setParams({ page })} />
          </>
        )}
      </div>
    </Dialog>
  );
}

/** One instructor per module. Changing one means removing the current person first. */
export function InstructorTab({ moduleId }: { moduleId: string }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const current = useModule(moduleId);
  const unassign = useUnassignInstructor(moduleId);
  const [picking, setPicking] = useState(false);
  const [removing, setRemoving] = useState(false);

  if (current.isPending) {
    return <Skeleton variant="card" />;
  }
  if (current.isError) {
    return <ErrorState onRetry={() => current.refetch()} message={t("staffCatalog.instructor.currentLoadError")} />;
  }

  // The single module read carries the instructor (G4); there is no directory scan.
  const person = current.data?.instructor ?? null;

  return (
    <div className="grid gap-4">
      {person ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-4">
          <div>
            <p className="font-medium">{person.name}</p>
            <p className="text-meta text-muted" dir="ltr">{person.phone ?? ""}</p>
          </div>
          <Button variant="danger" className="min-h-10" onClick={() => setRemoving(true)}>
            {t("staffCatalog.instructor.remove")}
          </Button>
        </div>
      ) : (
        <EmptyState
          title={t("staffCatalog.instructor.emptyTitle")}
          body={t("staffCatalog.instructor.emptyBody")}
          action={<Button onClick={() => setPicking(true)}>{t("staffCatalog.instructor.give")}</Button>}
        />
      )}
      {person ? (
        <p className="text-meta text-muted">{t("staffCatalog.instructor.oneOnly")}</p>
      ) : null}

      <InstructorPicker moduleId={moduleId} open={picking} onOpenChange={setPicking} />

      <ConfirmDialog
        open={removing}
        onOpenChange={setRemoving}
        title={t("staffCatalog.instructor.removeTitle", { name: person?.name ?? "" })}
        body={t("staffCatalog.instructor.removeBody")}
        confirmLabel={t("staffCatalog.instructor.removeConfirm")}
        tone="danger"
        onConfirm={async () => {
          try {
            await unassign.mutateAsync();
            toast({ title: t("staffCatalog.instructor.removed"), tone: "ok" });
          } catch (e) {
            throw new Error(t(catalogErrorKey(e)));
          }
        }}
      />
    </div>
  );
}
