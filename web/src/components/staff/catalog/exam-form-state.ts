import type { ChecklistItem } from "@/components/staff/catalog/exam-checklist";
import type { ExamInput, StaffExam } from "@/lib/api/exam/staff-catalog";
import type { PaperLanguage } from "@/lib/api/exam/types";

/** The sections of the exam page that save on their own button (plan 7.2). Questions and the header are not here. */
export type ExamSection = "details" | "text" | "schedule" | "price";

/** Every editable exam field as text, the way the inputs hold it. `""` means not set. */
export type ExamFormState = {
  titleEn: string;
  titleBn: string;
  /** The category id, or `""` while none is chosen. Any exam can change it (a package exam too). */
  categoryId: string;
  paperLanguage: PaperLanguage | "";
  previewEnabled: boolean;
  descriptionEn: string;
  descriptionBn: string;
  rulesEn: string;
  rulesBn: string;
  mode: "LIVE" | "PRACTICE";
  dated: boolean;
  liveAt: string | null;
  endAt: string | null;
  durationMinutes: string;
  passPercent: string;
  maximum: string;
  negativeOn: boolean;
  negativeMarks: string;
  answersChangeable: boolean;
  price: string;
  free: boolean;
  discountPercent: string;
  discountEndsAt: string | null;
  accessDays: string;
};

/** Field names mapped to a message key. The key `window` is the start and end check, shown on the end field. */
export type ExamFormErrors = Partial<Record<keyof ExamFormState | "window", string>>;

/** The negative mark a teacher gets when the switch is first turned on (plan 7.2). */
export const SUGGESTED_NEGATIVE_MARKS = "0.25";

/** The form as the saved exam reads. A null paper language is an empty choice; nothing is shown for it. */
export function examFormState(exam: StaffExam): ExamFormState {
  const negative = exam.negativeMarks ?? 0;
  return {
    titleEn: exam.titleEn,
    titleBn: exam.titleBn,
    categoryId: exam.categoryId ?? "",
    paperLanguage: exam.paperLanguage ?? "",
    previewEnabled: exam.previewEnabled ?? false,
    descriptionEn: exam.descriptionEn ?? "",
    descriptionBn: exam.descriptionBn ?? "",
    rulesEn: exam.rulesEn ?? "",
    rulesBn: exam.rulesBn ?? "",
    mode: exam.mode ?? "LIVE",
    dated: exam.liveAt !== null,
    liveAt: exam.liveAt,
    endAt: exam.endAt,
    durationMinutes: String(exam.durationMinutes),
    passPercent: exam.passPercent == null ? "" : String(exam.passPercent),
    maximum: String(exam.maximum),
    negativeOn: negative > 0,
    negativeMarks: negative > 0 ? String(negative) : SUGGESTED_NEGATIVE_MARKS,
    answersChangeable: exam.answersChangeable ?? true,
    price: exam.price == null ? "" : String(exam.price),
    free: exam.free ?? false,
    discountPercent: exam.discountPercent == null ? "" : String(exam.discountPercent),
    discountEndsAt: exam.discountEndsAt ?? null,
    accessDays: exam.accessDays == null ? "" : String(exam.accessDays),
  };
}

/** The fields each section sends. A section is dirty when one of them differs from the saved state. */
export const SECTION_KEYS: Record<ExamSection, (keyof ExamFormState)[]> = {
  details: ["titleEn", "titleBn", "categoryId", "paperLanguage", "previewEnabled"],
  text: ["descriptionEn", "descriptionBn", "rulesEn", "rulesBn"],
  schedule: ["mode", "dated", "liveAt", "endAt", "durationMinutes", "passPercent", "maximum", "negativeOn", "negativeMarks", "answersChangeable"],
  price: ["price", "free", "discountPercent", "discountEndsAt", "accessDays"],
};

/** What the section validation needs to know about the exam. */
export type ExamFormContext = {
  /** A written exam has a maximum and no negative marks; an MCQ exam has the reverse. */
  written: boolean;
  /** Only the Master Admin sets the price, the discount and the access. */
  canSetPrice: boolean;
};

const WHOLE = /^\d+$/;

function parseNumber(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === "") {
    return null;
  }
  const number = Number(trimmed);
  return Number.isFinite(number) ? number : null;
}

/** The window is checked against the duration: the end must come after the start and cover the duration (EXAM_WINDOW_INVALID). */
function windowErrors(form: ExamFormState): ExamFormErrors {
  if (form.mode !== "LIVE" || !form.dated) {
    return {};
  }
  if (!form.liveAt) {
    return { liveAt: "staffCatalog.examMeta.startRequired" };
  }
  if (!form.endAt) {
    return {};
  }
  const start = new Date(form.liveAt).getTime();
  const end = new Date(form.endAt).getTime();
  const minutes = Number(form.durationMinutes);
  if (end <= start) {
    return { window: "staffCatalog.examMeta.windowOrder" };
  }
  if (Number.isInteger(minutes) && minutes >= 2 && end - start < minutes * 60_000) {
    return { window: "staffCatalog.examMeta.windowShort" };
  }
  return {};
}

/**
 * The errors of one section, as message keys. The checks match the API: the window against the duration, a discount
 * from 1 to 99, a free exam with no price or discount, and an access period of at least one day.
 */
export function examFormErrors(form: ExamFormState, section: ExamSection, context: ExamFormContext): ExamFormErrors {
  const errors: ExamFormErrors = {};
  if (section === "details") {
    if (!form.titleEn.trim()) {
      errors.titleEn = "staffCatalog.examMeta.titleEnRequired";
    }
    if (!form.titleBn.trim()) {
      errors.titleBn = "staffCatalog.examMeta.titleBnRequired";
    }
    if (!form.categoryId) {
      errors.categoryId = "staffCatalog.examMeta.categoryRequired";
    }
  }
  if (section === "schedule") {
    const duration = Number(form.durationMinutes);
    if (!Number.isInteger(duration) || duration < 2) {
      errors.durationMinutes = "staffCatalog.examMeta.durationInvalid";
    }
    if (form.passPercent.trim() !== "") {
      const pass = Number(form.passPercent);
      if (!Number.isInteger(pass) || pass < 0 || pass > 100) {
        errors.passPercent = "staffCatalog.examMeta.passInvalid";
      }
    }
    if (context.written) {
      const maximum = Number(form.maximum);
      if (!Number.isInteger(maximum) || maximum < 1) {
        errors.maximum = "staffCatalog.examMeta.maximumInvalid";
      }
    }
    if (!context.written && form.negativeOn) {
      const marks = parseNumber(form.negativeMarks);
      if (marks === null || marks <= 0 || !/^\d+(\.\d{1,2})?$/.test(form.negativeMarks.trim())) {
        errors.negativeMarks = "staffCatalog.examMeta.negativeInvalid";
      }
    }
    Object.assign(errors, windowErrors(form));
  }
  if (section === "price" && context.canSetPrice) {
    if (form.free) {
      if (form.price.trim() !== "") {
        errors.price = "staffCatalog.examMeta.freeNoPrice";
      }
      if (form.discountPercent.trim() !== "") {
        errors.discountPercent = "staffCatalog.examMeta.freeNoPrice";
      }
    } else if (form.price.trim() !== "") {
      // A price is above zero, with at most two decimal places (the same rule as the API and the package price).
      const price = parseNumber(form.price);
      if (price === null || price <= 0 || !/^\d+(\.\d{1,2})?$/.test(form.price.trim())) {
        errors.price = "staffCatalog.examMeta.priceInvalid";
      }
    }
    if (form.discountPercent.trim() !== "") {
      const discount = Number(form.discountPercent);
      if (!WHOLE.test(form.discountPercent.trim()) || discount < 1 || discount > 99) {
        errors.discountPercent = "staffCatalog.examMeta.discountInvalid";
      }
    } else if (form.discountEndsAt) {
      errors.discountEndsAt = "staffCatalog.examMeta.discountEndNeedsDiscount";
    }
    if (form.accessDays.trim() !== "") {
      const days = Number(form.accessDays);
      if (!WHOLE.test(form.accessDays.trim()) || days < 1) {
        errors.accessDays = "staffCatalog.examMeta.accessDaysInvalid";
      }
    }
  }
  return errors;
}

/** The request body of one section. Empty optional fields are sent as null, so clearing a field clears it on the server. */
export function examBodyFor(form: ExamFormState, section: ExamSection, context: ExamFormContext): ExamInput {
  if (section === "details") {
    return {
      titleEn: form.titleEn.trim(),
      titleBn: form.titleBn.trim(),
      categoryId: form.categoryId || null,
      paperLanguage: form.paperLanguage || null,
      previewEnabled: form.previewEnabled,
    } as ExamInput;
  }
  if (section === "text") {
    return {
      descriptionEn: form.descriptionEn.trim(),
      descriptionBn: form.descriptionBn.trim(),
      rulesEn: form.rulesEn.trim(),
      rulesBn: form.rulesBn.trim(),
    };
  }
  if (section === "schedule") {
    const window = form.mode === "LIVE" && form.dated;
    const body: ExamInput = {
      mode: form.mode,
      liveAt: window ? form.liveAt : null,
      endAt: window ? form.endAt : null,
      durationMinutes: Number(form.durationMinutes),
      passPercent: form.passPercent.trim() === "" ? null : Number(form.passPercent),
    };
    if (context.written) {
      body.maximum = Number(form.maximum);
    } else {
      body.negativeMarks = form.negativeOn ? Number(form.negativeMarks) : 0;
      body.answersChangeable = form.answersChangeable;
    }
    return body;
  }
  const discount = form.discountPercent.trim() === "" ? null : Number(form.discountPercent);
  return {
    free: form.free,
    price: form.free || form.price.trim() === "" ? null : Number(form.price),
    discountPercent: discount,
    discountEndsAt: discount === null ? null : form.discountEndsAt,
    accessDays: form.accessDays.trim() === "" ? null : Number(form.accessDays),
  };
}

/** Whether the section's fields differ from what was saved. */
export function sectionDirty(current: ExamFormState, saved: ExamFormState, section: ExamSection): boolean {
  return SECTION_KEYS[section].some((key) => JSON.stringify(current[key]) !== JSON.stringify(saved[key]));
}

/** The descriptions and rules, in both languages. Publish needs all four (the server checks the same). */
export function hasExamText(exam: StaffExam): boolean {
  return [exam.descriptionEn, exam.descriptionBn, exam.rulesEn, exam.rulesBn].every((value) => Boolean(value?.trim()));
}

/** Whether the saved window fits the duration. An undated or practice exam has no window to check. */
export function windowFits(exam: StaffExam): boolean {
  if (exam.mode === "PRACTICE" || exam.liveAt === null || exam.endAt === null) {
    return true;
  }
  return new Date(exam.endAt).getTime() - new Date(exam.liveAt).getTime() >= exam.durationMinutes * 60_000;
}

/** What publish waits for, each item linked to the place that fixes it. */
export function examChecklist(exam: StaffExam, t: (key: string) => string): ChecklistItem[] {
  const items: ChecklistItem[] = [
    { key: "passPercent", label: t("staffCatalog.checklist.passPercent"), done: exam.passPercent !== null, anchor: "exam-schedule" },
    { key: "window", label: t("staffCatalog.checklist.window"), done: windowFits(exam), anchor: "exam-schedule" },
    { key: "examText", label: t("staffCatalog.checklist.examText"), done: hasExamText(exam), anchor: "exam-text" },
  ];
  if (exam.examType === "MCQ") {
    items.push({
      key: "questions",
      label: t("staffCatalog.checklist.questions"),
      done: (exam.questions?.length ?? 0) > 0,
      anchor: "exam-questions",
    });
  } else {
    items.push({
      key: "questionPdf",
      label: t("staffCatalog.checklist.questionPdf"),
      done: exam.questionPdf === true,
      anchor: "exam-question-pdf",
    });
    items.push({ key: "maximum", label: t("staffCatalog.checklist.maximum"), done: exam.maximum > 0, anchor: "exam-schedule" });
  }
  return items;
}

/** A draft can be published when every checklist item is done. A published exam is published through its change list. */
export function examPublishable(exam: StaffExam, items: ChecklistItem[]): boolean {
  return exam.status === "DRAFT" && items.every((item) => item.done);
}

/** The props every section's fields take. `errors` holds the message text, already translated. */
export type SectionFieldsProps = {
  exam: StaffExam;
  form: ExamFormState;
  set: <K extends keyof ExamFormState>(key: K, value: ExamFormState[K]) => void;
  errors: ExamFormErrors;
  disabled: boolean;
  context: ExamFormContext;
  /** The values as last saved, so a field can show whether it has unsaved changes. */
  saved: ExamFormState;
  /** True while the section is being saved. */
  saving: boolean;
  /** Saves the section from inside a field group, the same as the section's own Save button. */
  onSave: () => void;
};

/**
 * The HTML a plain text row opens as: one paragraph per line (the editor's own reading of it, plan 7.6). Only the
 * characters the HTML serializer escapes in text are escaped here.
 */
function plainAsHtml(text: string): string {
  return text
    .split("\n")
    .map((line) => `<p>${line.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")}</p>`)
    .join("");
}

/**
 * The value to keep for what the rich text editor reports. An emptied editor reports "<p></p>", stored as "". A plain
 * text row that the editor re-shapes into one paragraph per line keeps its stored text, so typing a character and then
 * deleting it does not mark the section changed. (The editor no longer reports the stored value when it opens.)
 */
export function richTextValue(reported: string, previous: string): string {
  if (reported.replace(/<\/?p>|<br\s*\/?>|\s/g, "") === "") {
    return "";
  }
  if (previous !== "" && !previous.includes("<") && reported === plainAsHtml(previous)) {
    return previous;
  }
  return reported;
}
