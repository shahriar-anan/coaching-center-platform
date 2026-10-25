"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useRole, RequireStaff } from "@/lib/auth/guards";
import { useModule } from "@/lib/api/exam/staff-catalog";
import { PageHeader } from "@/components/ui/page-header";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { ModuleDetails } from "@/components/staff/catalog/module-details";
import { ModuleExams } from "@/components/staff/catalog/module-exams";
import { RosterTab } from "@/components/staff/catalog/roster-tab";
import { InstructorTab } from "@/components/staff/catalog/instructor-tab";

const TABS = ["details", "exams", "roster", "instructor"] as const;

function ModuleEditor() {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const role = useRole();
  const { id } = useParams<{ id: string }>();
  const moduleQuery = useModule(id);
  const [params, setParams] = useListParams({ tab: "details" });

  if (moduleQuery.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (moduleQuery.isError || !moduleQuery.data) {
    return <ErrorState onRetry={() => moduleQuery.refetch()} message={t("staffCatalog.moduleForm.loadError")} />;
  }
  const data = moduleQuery.data;
  const title = pickTitle(data);
  const tab = (TABS as readonly string[]).includes(params.tab) ? params.tab : "details";
  const tabs = [
    { value: "details", label: t("staffCatalog.moduleTabs.details") },
    { value: "exams", label: t("staffCatalog.moduleTabs.exams") },
    ...(role.canManageRoster ? [{ value: "roster", label: t("staffCatalog.moduleTabs.roster") }] : []),
    ...(role.canAssignInstructor ? [{ value: "instructor", label: t("staffCatalog.moduleTabs.instructor") }] : []),
  ];
  const activeTab = tabs.some((item) => item.value === tab) ? tab : "details";

  return (
    <>
      <PageHeader
        title={title}
        breadcrumbs={[
          { label: t("staffCatalog.nav.home"), href: "/staff" },
          { label: t("staffCatalog.modules.title"), href: "/staff/modules" },
          { label: title },
        ]}
        actions={<StatusPill status={data.status} />}
      />
      <div className="grid gap-6">
        <Tabs tabs={tabs} value={activeTab} onChange={(value) => setParams({ tab: value })} ariaLabel={t("staffCatalog.moduleTabs.label")} />
        {activeTab === "details" ? <ModuleDetails module={data} /> : null}
        {activeTab === "exams" ? <ModuleExams moduleId={data.id} /> : null}
        {activeTab === "roster" && role.canManageRoster ? <RosterTab moduleId={data.id} /> : null}
        {activeTab === "instructor" && role.canAssignInstructor ? <InstructorTab moduleId={data.id} /> : null}
      </div>
    </>
  );
}

function ModulePage() {
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <ModuleEditor />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <ModulePage />
    </Suspense>
  );
}
