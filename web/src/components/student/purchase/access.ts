import type { AccessInfo, AccessState, BlockedReason } from "@/lib/api/exam/types";

export type ResolvedAccess = {
  owned: boolean;
  via: Exclude<AccessState, "NONE"> | null;
  /** Null when the API did not send the access object: the page keeps its current behaviour. */
  buyable: boolean | null;
  blockedReason: BlockedReason | null;
};

/**
 * Reads the `access` object when the API sends it. When it is absent (an older API), it falls back
 * to the ownership probe the page already makes.
 * fallback until A1 access object is merged; delete after
 */
export function resolveAccess(access: AccessInfo | null | undefined, probeOwned: boolean): ResolvedAccess {
  if (access) {
    return {
      owned: access.state !== "NONE",
      via: access.state === "NONE" ? null : access.state,
      buyable: access.buyable,
      blockedReason: access.blockedReason,
    };
  }
  return { owned: probeOwned, via: null, buyable: null, blockedReason: null };
}

/** The copy key for a blocked reason. Null for no reason. */
export function blockedMessageKey(reason: BlockedReason | null): string | null {
  return reason ? `purchase.blocked.${reason}` : null;
}
