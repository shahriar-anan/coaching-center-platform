/**
 * Facts about the school that the landing page shows. They change rarely and
 * are not in the API, so they live here and are edited in code.
 * Phone numbers are stored in international form for `tel:` and `wa.me` links.
 */

export const phones = [
  { display: "01601-561225", href: "+8801601561225" },
  { display: "01968-322239", href: "+8801968322239" },
] as const;

/** WhatsApp takes the number without the plus sign. */
export const whatsappNumber = "8801601561225";

export const email = "info@symamlaw.com";

export const officeHours = "10:00 AM - 6:00 PM";

export const address = {
  bn: "বিনোদপুর বাজার (কৃষি ব্যাংকের পশ্চিম পার্শ্বে, ওয়ালটন শো-রুমের অপজিটে), মতিহার, রাজশাহী",
  en: "Binodpur Bazar, Motihar, Rajshahi, Bangladesh",
};

export const mapUrl =
  "https://www.google.com/maps/search/?api=1&query=Binodpur+Bazar+Motihar+Rajshahi";

export const socials = {
  facebook: "https://www.facebook.com/symamlaw",
  youtube: "https://youtu.be/ZmrfDj6T5R4",
  linkedin: "https://www.linkedin.com/company/symam-law/",
} as const;

/**
 * The batches on the client's poster, kept in the poster's own English names.
 * `kind` decides the link: MCQ and written batches open the exam site;
 * the others are offline and are only listed (see the landing page plan, Q2).
 */
export const batches = {
  bjs: [
    { name: "BJS Foundation Batch", kind: "foundation" },
    { name: "BJS MCQ Exam Batch", kind: "mcq" },
    { name: "BJS WRITTEN Exam Batch", kind: "written" },
  ],
  bar: [
    { name: "Bar Council Special Batch", kind: "special" },
    { name: "BAR MCQ Exam Batch", kind: "mcq" },
    { name: "BAR WRITTEN Exam Batch", kind: "written" },
  ],
} as const;

/** Module ids to feature, in order. Empty means the first modules from the catalog. */
export const featuredModuleIds: readonly string[] = [];

/**
 * Registered students, as the old site's home page shows them (765+ on 26 Oct 2026).
 * Update this figure by hand before the cutover.
 */
export const registeredStudents = 765;
