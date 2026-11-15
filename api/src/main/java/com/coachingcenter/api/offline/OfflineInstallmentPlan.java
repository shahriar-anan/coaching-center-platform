package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The instalments of an admission before onboarding (instalment-plan-and-early-payments.md, E1, E2). The admin may set each row's date
 * and amount; the course fee is then their sum. Without saved rows the plan is the even split of the course fee over the number of
 * instalments, one a month from the first due date, as the course fee release made it.
 */
final class OfflineInstallmentPlan {

	/** One instalment: its due date and its amount. */
	record Row(LocalDate dueOn, BigDecimal amount) {
	}

	private OfflineInstallmentPlan() {
	}

	/** The admission's instalments: its own rows when it has them, else the even split. Empty until the fee, count and date are set. */
	static List<Row> of(OfflineAdmission a, ObjectMapper json) {
		if (a.getInstallmentPlan() != null) {
			List<Row> rows = new ArrayList<>();
			for (JsonNode node : json.readTree(a.getInstallmentPlan())) {
				rows.add(new Row(LocalDate.parse(node.get("dueOn").asText()), new BigDecimal(node.get("amount").asText()).setScale(2,
						RoundingMode.HALF_UP)));
			}
			return rows;
		}
		return even(a.getCourseFee(), a.getInstallments(), a.getFirstDueOn());
	}

	/** The even split: whole taka for each but the last, which takes the rest, one a month from the first due date. */
	static List<Row> even(BigDecimal fee, Integer count, LocalDate firstDueOn) {
		if (fee == null || count == null || count < 1 || firstDueOn == null) {
			return List.of();
		}
		List<BigDecimal> amounts = OfflineFeeSchedule.split(fee, count);
		List<Row> rows = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			rows.add(new Row(OfflineFeeSchedule.installmentDate(firstDueOn, index + 1), amounts.get(index)));
		}
		return rows;
	}

	/**
	 * Reads the rows a setup sends, as [{dueOn, amount}]. Problems go on {@code installmentPlan} with the row's number. Null when the body
	 * does not name the field.
	 */
	static List<Row> parse(JsonNode body, List<FieldErrorDetail> problems) {
		if (body == null || !body.has("installmentPlan") || body.get("installmentPlan").isNull()) {
			return null;
		}
		JsonNode list = body.get("installmentPlan");
		if (!list.isArray() || list.isEmpty() || list.size() > OfflineFeeSchedule.MAX_INSTALLMENTS) {
			problems.add(new FieldErrorDetail("installmentPlan", "Give from 1 to " + OfflineFeeSchedule.MAX_INSTALLMENTS + " instalments."));
			return null;
		}
		List<Row> rows = new ArrayList<>();
		int number = 0;
		for (JsonNode node : list) {
			number++;
			LocalDate dueOn = null;
			BigDecimal amount = null;
			try {
				dueOn = LocalDate.parse(node.path("dueOn").asText(""));
			}
			catch (DateTimeParseException exception) {
				problems.add(new FieldErrorDetail("installmentPlan", "Instalment " + number + ": enter its due date."));
			}
			try {
				amount = new BigDecimal(node.path("amount").asText("").trim());
				if (amount.compareTo(BigDecimal.ONE) < 0 || amount.stripTrailingZeros().scale() > 2) {
					problems.add(new FieldErrorDetail("installmentPlan",
							"Instalment " + number + ": the amount must be at least ৳1, with at most two decimals."));
					amount = null;
				}
			}
			catch (NumberFormatException exception) {
				problems.add(new FieldErrorDetail("installmentPlan", "Instalment " + number + ": enter the amount as a number."));
			}
			if (dueOn != null && amount != null) {
				rows.add(new Row(dueOn, amount.setScale(2, RoundingMode.HALF_UP)));
			}
		}
		return rows.size() == list.size() ? rows : null;
	}

	/** Keeps the admin's rows on the admission: the course fee is their sum, the count their number, the first due date the first date. */
	static void apply(OfflineAdmission a, List<Row> rows, ObjectMapper json) {
		ArrayNode list = json.createArrayNode();
		BigDecimal total = BigDecimal.ZERO;
		for (Row row : rows) {
			ObjectNode node = list.addObject();
			node.put("dueOn", row.dueOn().toString());
			node.put("amount", row.amount().toPlainString());
			total = total.add(row.amount());
		}
		a.setInstallmentPlan(json.writeValueAsString(list));
		a.setCourseFee(total.setScale(2, RoundingMode.HALF_UP));
		a.setInstallments(rows.size());
		a.setFirstDueOn(rows.get(0).dueOn());
		a.setMonthlyFee(rows.get(0).amount());
	}

	/**
	 * What is wrong with the rows against the admission's dates (E1): each date must be after the one before it, the first on or after
	 * the admitted-on date, and the last on or before the course end.
	 */
	static void problems(OfflineAdmission a, List<Row> rows, List<FieldErrorDetail> problems) {
		for (int index = 1; index < rows.size(); index++) {
			if (!rows.get(index).dueOn().isAfter(rows.get(index - 1).dueOn())) {
				problems.add(new FieldErrorDetail("installmentPlan",
						"Instalment " + (index + 1) + " must be due after instalment " + index + "."));
			}
		}
		if (rows.isEmpty()) {
			return;
		}
		if (a.getAdmittedOn() != null && rows.get(0).dueOn().isBefore(a.getAdmittedOn())) {
			problems.add(new FieldErrorDetail("installmentPlan", "The first instalment cannot be due before the admitted-on date."));
		}
		LocalDate last = rows.get(rows.size() - 1).dueOn();
		if (a.getCourseEndsOn() != null && last.isAfter(a.getCourseEndsOn())) {
			problems.add(new FieldErrorDetail("installmentPlan", "The last instalment is due on " + last + ", after the course ends on "
					+ a.getCourseEndsOn() + ". Move it earlier, or set a later course end."));
		}
	}

}
