"use client";

import { useState } from "react";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { AccountingFrame } from "../accounting-frame";
import { AccountsSettings } from "./accounts-settings";
import { GroupsSettings } from "./groups-settings";
import { TypesSettings } from "./types-settings";

type SettingsTab = "groups" | "types" | "accounts";

/** Settings (accounting-plan 4.6): three tabs for groups, the types of a group, and money accounts. */
export function AccountingSettings() {
  const { t } = useLocale();
  const [tab, setTab] = useState<SettingsTab>("groups");
  return (
    <AccountingFrame title={t("accounting.settings.title")} breadcrumbs={[{ label: t("accounting.settings.title") }]}>
      <div className="grid gap-5">
        <Tabs
          ariaLabel={t("accounting.settings.title")}
          tabs={[
            { value: "groups", label: t("accounting.settings.tabs.groups") },
            { value: "types", label: t("accounting.settings.tabs.types") },
            { value: "accounts", label: t("accounting.settings.tabs.accounts") },
          ]}
          value={tab}
          onChange={(value) => setTab(value as SettingsTab)}
        />
        {tab === "groups" ? <GroupsSettings /> : null}
        {tab === "types" ? <TypesSettings /> : null}
        {tab === "accounts" ? <AccountsSettings /> : null}
      </div>
    </AccountingFrame>
  );
}
