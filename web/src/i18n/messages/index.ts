/**
 * Every message namespace, registered once here so no workstream edits this
 * file. Keys are `namespace.key`. The legacy catalog (old admin pages) sits
 * underneath; a namespace key with the same path wins.
 */
import type { MessageTree } from "./types";

import bnLegacy from "./bn/legacy";
import bnCommon from "./bn/common";
import bnAuth from "./bn/auth";
import bnAccount from "./bn/account";
import bnNotices from "./bn/notices";
import bnLegal from "./bn/legal";
import bnCatalog from "./bn/catalog";
import bnPurchase from "./bn/purchase";
import bnHome from "./bn/home";
import bnLanding from "./bn/landing";
import bnSitting from "./bn/sitting";
import bnResult from "./bn/result";
import bnStaffCatalog from "./bn/staffCatalog";
import bnStaffPeople from "./bn/staffPeople";
import bnStaffOps from "./bn/staffOps";
import bnStaffMarking from "./bn/staffMarking";
import bnStaffResults from "./bn/staffResults";
import bnStaffAdmin from "./bn/staffAdmin";

import enLegacy from "./en/legacy";
import enCommon from "./en/common";
import enAuth from "./en/auth";
import enAccount from "./en/account";
import enNotices from "./en/notices";
import enLegal from "./en/legal";
import enCatalog from "./en/catalog";
import enPurchase from "./en/purchase";
import enHome from "./en/home";
import enLanding from "./en/landing";
import enSitting from "./en/sitting";
import enResult from "./en/result";
import enStaffCatalog from "./en/staffCatalog";
import enStaffPeople from "./en/staffPeople";
import enStaffOps from "./en/staffOps";
import enStaffMarking from "./en/staffMarking";
import enStaffResults from "./en/staffResults";
import enStaffAdmin from "./en/staffAdmin";

export type { MessageTree };

function merge(base: MessageTree, over: MessageTree): MessageTree {
  const out: MessageTree = { ...base };
  for (const [key, value] of Object.entries(over)) {
    const current = out[key];
    out[key] =
      typeof value === "object" && typeof current === "object"
        ? merge(current, value)
        : value;
  }
  return out;
}

export const bn: MessageTree = merge(bnLegacy as MessageTree, {
  common: bnCommon,
  auth: bnAuth,
  account: bnAccount,
  notices: bnNotices,
  legal: bnLegal,
  catalog: bnCatalog,
  purchase: bnPurchase,
  home: bnHome,
  landing: bnLanding,
  sitting: bnSitting,
  result: bnResult,
  staffCatalog: bnStaffCatalog,
  staffPeople: bnStaffPeople,
  staffOps: bnStaffOps,
  staffMarking: bnStaffMarking,
  staffResults: bnStaffResults,
  staffAdmin: bnStaffAdmin,
});

export const en: MessageTree = merge(enLegacy as MessageTree, {
  common: enCommon,
  auth: enAuth,
  account: enAccount,
  notices: enNotices,
  legal: enLegal,
  catalog: enCatalog,
  purchase: enPurchase,
  home: enHome,
  landing: enLanding,
  sitting: enSitting,
  result: enResult,
  staffCatalog: enStaffCatalog,
  staffPeople: enStaffPeople,
  staffOps: enStaffOps,
  staffMarking: enStaffMarking,
  staffResults: enStaffResults,
  staffAdmin: enStaffAdmin,
});

export const catalogs = { bn, en };
