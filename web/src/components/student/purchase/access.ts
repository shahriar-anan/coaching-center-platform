import type { AccessInfo, AccessState, BlockedReason } from "@/lib/api/exam/types";

export type ResolvedAccess = {
  owned: boolean;
  via: Exclude<AccessState, "NONE"> | null;
  /** Null when the API did not send the access object (visitors and staff). */
  buyable: boolean | null;
  blockedReason: BlockedReason | null;
};

/** Reads the `access` object on a module or exam response (S-003). Absent means no access to show. */
export function resolveAccess(access: AccessInfo | null | undefined): ResolvedAccess {
  if (!access) return { owned: false, via: null, buyable: null, blockedReason: null };
  return {
    owned: access.state !== "NONE",
    via: access.state === "NONE" ? null : access.state,
    buyable: access.buyable,
    blockedReason: access.blockedReason,
  };
}

/** The copy key for a blocked reason. Null for no reason. */
export function blockedMessageKey(reason: BlockedReason | null): string | null {
  return reason ? `purchase.blocked.${reason}` : null;
}
