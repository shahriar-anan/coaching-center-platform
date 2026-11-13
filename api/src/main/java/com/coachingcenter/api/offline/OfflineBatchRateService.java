package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;

/**
 * The default pay rate of each instructor within a batch (plan F7, 4.4). Staff and the Master Admin set them on the batch page. Setting
 * a rate changes the amount of every unsettled class of that instructor in the batch that has no override; a settled class keeps the
 * amount it was paid on. Removing a rate changes no class: each keeps the amount it has until a rate is set again.
 */
@Service
public class OfflineBatchRateService {

	public static final String SET = "OFFLINE_BATCH_RATE_SET";

	public static final String REMOVED = "OFFLINE_BATCH_RATE_REMOVED";

	/** The largest value numeric(12,2) holds. */
	private static final BigDecimal MAX_RATE = new BigDecimal("9999999999.99");

	private final OfflineBatchRepository batches;

	private final OfflineInstructorRepository instructors;

	private final OfflineBatchInstructorRateRepository rates;

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	public OfflineBatchRateService(OfflineBatchRepository batches, OfflineInstructorRepository instructors,
			OfflineBatchInstructorRateRepository rates, JdbcTemplate jdbc, AuditRecorder audit, Clock clock) {
		this.batches = batches;
		this.instructors = instructors;
		this.rates = rates;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** The rates of a batch, by instructor name. An instructor with no rate yet is not listed. */
	@Transactional(readOnly = true)
	public List<RateView> list(UUID batchId) {
		findBatch(batchId);
		List<OfflineBatchInstructorRate> found = rates.findByBatchId(batchId);
		Map<UUID, String> names = instructors.findAllById(found.stream().map(OfflineBatchInstructorRate::getInstructorId).toList())
				.stream().collect(Collectors.toMap(OfflineInstructor::getId, OfflineInstructor::getName));
		List<RateView> views = new ArrayList<>();
		for (OfflineBatchInstructorRate rate : found) {
			views.add(new RateView(rate.getInstructorId(), names.getOrDefault(rate.getInstructorId(), ""), rate.getRate()));
		}
		views.sort(Comparator.comparing(RateView::name, String.CASE_INSENSITIVE_ORDER));
		return views;
	}

	/**
	 * Sets (or replaces) the rate of an instructor in a batch, then updates each unsettled class of that instructor in the batch that has
	 * no override. The classes that are settled, or cancelled, keep their amounts.
	 */
	@Transactional
	public RateView set(UUID actorId, UUID batchId, UUID instructorId, JsonNode body) {
		OfflineBatch batch = findBatch(batchId);
		requireActive(batch);
		OfflineInstructor instructor = findInstructor(instructorId);
		if (instructor.getArchivedAt() != null) {
			throw state("This instructor is archived, so the rate cannot be set.");
		}
		BigDecimal rate = rate(body);
		Instant now = clock.instant();
		OfflineBatchInstructorRate row = rates.findById(new OfflineBatchInstructorRate.Key(batchId, instructorId)).orElseGet(() -> {
			OfflineBatchInstructorRate created = new OfflineBatchInstructorRate();
			created.setBatchId(batchId);
			created.setInstructorId(instructorId);
			return created;
		});
		row.setRate(rate);
		row.setUpdatedBy(actorId);
		row.setUpdatedAt(now);
		rates.saveAndFlush(row);
		jdbc.update("""
				update offline_classes set amount = ?, updated_at = ?, version = version + 1
				where batch_id = ? and instructor_id = ? and rate_override is null and status = ?
				""", rate, Timestamp.from(now), batchId, instructorId, OfflineClass.STATUS_SCHEDULED);
		audit.record(SET, AuditRecorder.OFFLINE_BATCH_ENTITY, batchId);
		return new RateView(instructorId, instructor.getName(), rate);
	}

	/** Removes the rate of an instructor in a batch. No class changes: each keeps the amount it has. */
	@Transactional
	public void remove(UUID batchId, UUID instructorId) {
		OfflineBatch batch = findBatch(batchId);
		requireActive(batch);
		OfflineBatchInstructorRate row = rates.findById(new OfflineBatchInstructorRate.Key(batchId, instructorId))
				.orElseThrow(OfflineSupport::notFound);
		rates.delete(row);
		rates.flush();
		audit.record(REMOVED, AuditRecorder.OFFLINE_BATCH_ENTITY, batchId);
	}

	private OfflineBatch findBatch(UUID batchId) {
		return batches.findById(batchId).orElseThrow(OfflineSupport::notFound);
	}

	private OfflineInstructor findInstructor(UUID instructorId) {
		return instructors.findById(instructorId).orElseThrow(OfflineSupport::notFound);
	}

	private static void requireActive(OfflineBatch batch) {
		if (!OfflineBatch.STATUS_ACTIVE.equals(batch.getStatus())) {
			throw state("This batch is archived, so its rates cannot be changed.");
		}
	}

	/** The rate in the body: required, a number, not negative, and within the column's size. */
	private static BigDecimal rate(JsonNode body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		if (!OfflineSupport.has(body, "rate")) {
			problems.add(new FieldErrorDetail("rate", "Enter the rate."));
			throw validation(problems);
		}
		BigDecimal rate;
		try {
			rate = new BigDecimal(body.get("rate").asText().trim()).setScale(2, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException exception) {
			problems.add(new FieldErrorDetail("rate", "Enter the rate as a number."));
			throw validation(problems);
		}
		if (rate.signum() < 0) {
			problems.add(new FieldErrorDetail("rate", "The rate cannot be negative."));
		}
		else if (rate.compareTo(MAX_RATE) > 0) {
			problems.add(new FieldErrorDetail("rate", "This rate is too large."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		return rate;
	}

	/** One rate: the instructor, their name, and the rate in this batch. */
	public record RateView(UUID instructorId, String name, BigDecimal rate) {
	}

}
