import bnBatchPay from "@/i18n/messages/bn/batchPay";
import enBatchPay from "@/i18n/messages/en/batchPay";

/**
 * Adds the `batchPay` namespace to the message catalogues for these tests. `index.ts` registers it for the app; until then the tests
 * merge it here, so the screens show real text and not keys. Pass the module's original exports in.
 */
export function withBatchPay<T extends { bn: Record<string, unknown>; en: Record<string, unknown> }>(mod: T) {
  const bn = { ...mod.bn, batchPay: bnBatchPay };
  const en = { ...mod.en, batchPay: enBatchPay };
  return { ...mod, bn, en, catalogs: { bn, en } };
}
