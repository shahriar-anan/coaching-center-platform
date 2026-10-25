import { catalogs } from "@/i18n/messages";
import bnStaffAdmin from "@/i18n/messages/bn/staffAdmin";
import enStaffAdmin from "@/i18n/messages/en/staffAdmin";

/**
 * Lets the tests read the real staffAdmin copy before the integrator registers
 * the namespace in `messages/index.ts`. Once it is registered, this is a no-op.
 */
catalogs.bn.staffAdmin = bnStaffAdmin;
catalogs.en.staffAdmin = enStaffAdmin;
