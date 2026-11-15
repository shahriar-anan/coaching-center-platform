package com.coachingcenter.api.exams;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.coachingcenter.api.common.mail.EmailTemplates;
import com.coachingcenter.api.common.mail.MailContext;
import com.coachingcenter.api.common.mail.MailRecipient;
import com.coachingcenter.api.common.mail.RenderedEmail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The four offline admission mails (plan section 7). Each is written once, with a dedupe key, and carries its own values in the
 * outbox payload, so the text does not change when the admission changes later. The payload is a flat object of strings; dates
 * are ISO (yyyy-MM-dd) and are shown as day, month and year.
 */
final class AdmissionMail {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String MY_ADMISSION = "/my-admission";

	private static final String FIX_FORM = "/admission/form";

	private AdmissionMail() {
	}

	static boolean handles(String kind) {
		return ExamOutboundMail.ADMISSION_APPROVED.equals(kind) || ExamOutboundMail.ADMISSION_CHANGES_REQUESTED.equals(kind)
				|| ExamOutboundMail.ADMISSION_UPDATED.equals(kind) || ExamOutboundMail.ADMISSION_CLOSED.equals(kind);
	}

	/** The mail for one kind, or null when the payload lacks what the kind needs (the row is then skipped). */
	static RenderedEmail render(MailContext ctx, String kind, MailRecipient to, JsonNode payload) {
		if (payload == null || !payload.isObject()) {
			return null;
		}
		return switch (kind) {
			case ExamOutboundMail.ADMISSION_APPROVED -> approved(ctx, to, payload);
			case ExamOutboundMail.ADMISSION_CHANGES_REQUESTED -> changesRequested(ctx, to, payload);
			case ExamOutboundMail.ADMISSION_UPDATED -> updated(ctx, to, payload);
			case ExamOutboundMail.ADMISSION_CLOSED -> closed(ctx, to, payload);
			default -> null;
		};
	}

	private static RenderedEmail approved(MailContext ctx, MailRecipient to, JsonNode p) {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "ব্যাচ", "Batch", both(p, "batchBn", "batchEn") });
		// The course fee is the total, in monthly instalments (V100). A mail queued before that still carries the monthly fee.
		if (text(p, "courseFee").isEmpty()) {
			rows.add(new String[] { "মাসিক ফি", "Monthly fee", text(p, "monthlyFee") + " BDT" });
		}
		else {
			rows.add(new String[] { "কোর্স ফি", "Course fee", text(p, "courseFee") + " BDT" });
			rows.add(new String[] { "মাসিক কিস্তি", "Monthly instalments", text(p, "installments") });
		}
		rows.add(new String[] { "প্রথম পরিশোধের তারিখ", "First due date", day(p, "firstDueOn") });
		rows.add(new String[] { "কোর্স শেষ", "Course ends", day(p, "courseEndsOn") });
		// Exams and packages are optional (P18): with none, the row and the sentence about them are left out.
		boolean hasItems = !text(p, "itemsEn").isEmpty();
		if (hasItems) {
			rows.add(new String[] { "পরীক্ষা ও প্যাকেজ", "Exams and packages", both(p, "itemsBn", "itemsEn") });
		}
		// P14: a student who already has another batch sees every batch they now have access to.
		if (text(p, "batchesEn").contains(";")) {
			rows.add(new String[] { "আপনার সব ব্যাচ", "Batches you have access to", both(p, "batchesBn", "batchesEn") });
		}
		return EmailTemplates.admissionNotice(ctx, to, "ভর্তি অনুমোদিত হয়েছে", "Admission approved",
				hasItems ? "আপনার ভর্তি অনুমোদিত হয়েছে। এখন থেকে নিচের পরীক্ষা ও প্যাকেজগুলো আপনার জন্য খোলা।"
						: "আপনার ভর্তি অনুমোদিত হয়েছে। ব্যাচের তথ্য নিচে দেওয়া হলো।",
				hasItems ? "your admission is approved. From now on the exams and packages below are open to you."
						: "your admission is approved. The details of your batch are below.", rows, "আমার ভর্তি দেখুন",
				"View my admission", ctx.site(MY_ADMISSION));
	}

	private static RenderedEmail changesRequested(MailContext ctx, MailRecipient to, JsonNode p) {
		String reason = text(p, "reason");
		if (reason.isEmpty()) {
			return null;
		}
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "কারণ", "Reason", reason });
		return EmailTemplates.admissionNotice(ctx, to, "ফর্মে পরিবর্তন দরকার", "Your form needs changes",
				"আপনার ভর্তি ফর্মটি ফেরত পাঠানো হয়েছে। নিচের কারণটি দেখে ফর্মটি ঠিক করে আবার জমা দিন।",
				"your admission form was sent back. Read the reason below, fix the form and submit it again.", rows, "ফর্ম ঠিক করুন",
				"Fix my form", ctx.site(FIX_FORM));
	}

	private static RenderedEmail updated(MailContext ctx, MailRecipient to, JsonNode p) {
		JsonNode changes = p.get("changes");
		if (changes == null || !changes.isArray() || changes.isEmpty()) {
			return null;
		}
		List<String[]> rows = new ArrayList<>();
		for (JsonNode change : changes) {
			rows.add(new String[] { text(change, "labelBn"), text(change, "labelEn"),
					text(change, "old") + " → " + text(change, "new") });
		}
		return EmailTemplates.admissionNotice(ctx, to, "ভর্তির তথ্য বদলেছে", "Your admission was updated",
				"আপনার ভর্তির তথ্যে পরিবর্তন হয়েছে। আগের ও নতুন মান নিচে দেওয়া হলো।",
				"your admission details changed. The old and new values are below.", rows, "আমার ভর্তি দেখুন", "View my admission",
				ctx.site(MY_ADMISSION));
	}

	private static RenderedEmail closed(MailContext ctx, MailRecipient to, JsonNode p) {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "বন্ধ হয়েছে", "Closed on", day(p, "closedOn") });
		String reason = text(p, "reason");
		if (!reason.isEmpty()) {
			rows.add(new String[] { "কারণ", "Reason", reason });
		}
		return EmailTemplates.admissionNotice(ctx, to, "ভর্তি বন্ধ হয়েছে", "Your admission is closed",
				"আপনার ভর্তি বন্ধ করা হয়েছে। আপনার ভর্তির ইতিহাস এখনো দেখতে পারবেন।",
				"your admission is closed. You can still read its history.", rows, "আমার ভর্তি দেখুন", "View my admission",
				ctx.site(MY_ADMISSION));
	}

	/** The Bangla and English values as one line, Bangla first, the way the rest of the mail is written. */
	private static String both(JsonNode p, String bnField, String enField) {
		String bn = text(p, bnField);
		String en = text(p, enField);
		if (bn.isEmpty()) {
			return en;
		}
		if (en.isEmpty() || en.equals(bn)) {
			return bn;
		}
		return bn + " / " + en;
	}

	private static String day(JsonNode p, String field) {
		String value = text(p, field);
		if (value.isEmpty()) {
			return "";
		}
		try {
			return DAY.format(LocalDate.parse(value));
		}
		catch (java.time.format.DateTimeParseException exception) {
			return value;
		}
	}

	/** The payload as a tree, or null when it is missing or not JSON (the row is then skipped, and nothing is sent). */
	static JsonNode parse(String payload) {
		if (payload == null || payload.isBlank()) {
			return null;
		}
		try {
			return JSON.readTree(payload);
		}
		catch (tools.jackson.core.JacksonException exception) {
			return null;
		}
	}

	private static String text(JsonNode p, String field) {
		JsonNode node = p.get(field);
		return node == null || node.isNull() ? "" : node.asText("");
	}

}
