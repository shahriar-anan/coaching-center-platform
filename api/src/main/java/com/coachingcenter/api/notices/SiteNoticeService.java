package com.coachingcenter.api.notices;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.common.web.PageResponse;

/**
 * The public notice board (refunds, stats and notice board plan, section 6). System Admins and the Master Admin draft and
 * edit notices; only the Master Admin publishes, unpublishes, edits or deletes a published notice. Every write is audited.
 */
@Service
public class SiteNoticeService {

	static final int MAX_TITLE = 300;

	static final int MAX_BODY = 10_000;

	static final int PUBLIC_PAGE_SIZE = 10;

	private static final String DENIED = "Access is denied.";

	private final SiteNoticeRepository notices;

	private final PermissionChecker permissions;

	private final AuditRecorder audit;

	private final Clock clock;

	public SiteNoticeService(SiteNoticeRepository notices, PermissionChecker permissions, AuditRecorder audit, Clock clock) {
		this.notices = notices;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/** One page of notices for staff, most recently changed first. {@code status} is optional; a blank value is no filter. */
	@Transactional(readOnly = true)
	public PageResponse<StaffRow> list(String status, int page, int size) {
		requireEdit();
		PageRequest request = PageRequest.of(Math.max(page, 0), size < 1 ? 20 : Math.min(size, 100));
		String filter = status == null || status.isBlank() ? null : status.trim();
		if (filter != null && !SiteNotice.DRAFT.equals(filter) && !SiteNotice.PUBLISHED.equals(filter)
				&& !SiteNotice.UNPUBLISHED.equals(filter)) {
			throw validation(List.of(new FieldErrorDetail("status", "Status must be DRAFT, PUBLISHED or UNPUBLISHED.")));
		}
		var rows = filter == null ? notices.findByDeletedAtIsNullOrderByUpdatedAtDescIdDesc(request)
				: notices.findByStatusAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(filter, request);
		return PageResponse.from(rows.map(SiteNoticeService::staffRow));
	}

	@Transactional(readOnly = true)
	public StaffRow get(UUID id) {
		requireEdit();
		return staffRow(find(id));
	}

	/** Creates a draft. The four texts may be blank, since a draft need not be complete. */
	@Transactional
	public StaffRow create(NoticeForm form) {
		requireEdit();
		UUID actor = actorId();
		Texts texts = texts(form, false);
		Instant now = clock.instant();
		SiteNotice notice = new SiteNotice();
		texts.applyTo(notice);
		notice.setStatus(SiteNotice.DRAFT);
		notice.setCreatedBy(actor);
		notice.setUpdatedBy(actor);
		notice.setCreatedAt(now);
		notice.setUpdatedAt(now);
		SiteNotice saved = notices.save(notice);
		audit.record(AuditRecorder.SITE_NOTICE_CREATE, AuditRecorder.SITE_NOTICE_ENTITY, saved.getId());
		return staffRow(saved);
	}

	/**
	 * Saves the four texts. A draft may be incomplete. A notice that has been published (status PUBLISHED or UNPUBLISHED)
	 * needs the Master Admin and must keep all four texts; an edit to a published notice shows at once.
	 */
	@Transactional
	public StaffRow update(UUID id, NoticeForm form) {
		SiteNotice notice = find(id);
		boolean wasPublished = !SiteNotice.DRAFT.equals(notice.getStatus());
		if (wasPublished) {
			requirePublish();
		}
		else {
			requireEdit();
		}
		Texts texts = texts(form, wasPublished);
		texts.applyTo(notice);
		notice.setUpdatedBy(actorId());
		notice.setUpdatedAt(clock.instant());
		SiteNotice saved = notices.save(notice);
		audit.record(AuditRecorder.SITE_NOTICE_UPDATE, AuditRecorder.SITE_NOTICE_ENTITY, id);
		return staffRow(saved);
	}

	/** Publishes a draft or an unpublished notice. All four texts must be present. The Master Admin only. */
	@Transactional
	public StaffRow publish(UUID id) {
		requirePublish();
		SiteNotice notice = find(id);
		if (SiteNotice.PUBLISHED.equals(notice.getStatus())) {
			throw new ApiException(ErrorCode.SITE_NOTICE_STATE, HttpStatus.CONFLICT, "This notice is already published.");
		}
		List<FieldErrorDetail> missing = new Texts(notice.getTitleEn(), notice.getTitleBn(), notice.getBodyEn(),
				notice.getBodyBn()).missing();
		if (!missing.isEmpty()) {
			throw validation(missing);
		}
		UUID actor = actorId();
		Instant now = clock.instant();
		notice.setStatus(SiteNotice.PUBLISHED);
		notice.setPublishedAt(now);
		notice.setPublishedBy(actor);
		notice.setUpdatedBy(actor);
		notice.setUpdatedAt(now);
		SiteNotice saved = notices.save(notice);
		audit.record(AuditRecorder.SITE_NOTICE_PUBLISH, AuditRecorder.SITE_NOTICE_ENTITY, id);
		return staffRow(saved);
	}

	/** Hides a published notice from the public. The Master Admin only. The texts and the publish date are kept. */
	@Transactional
	public StaffRow unpublish(UUID id) {
		requirePublish();
		SiteNotice notice = find(id);
		if (!SiteNotice.PUBLISHED.equals(notice.getStatus())) {
			throw new ApiException(ErrorCode.SITE_NOTICE_STATE, HttpStatus.CONFLICT,
					"Only a published notice can be unpublished.");
		}
		notice.setStatus(SiteNotice.UNPUBLISHED);
		notice.setUpdatedBy(actorId());
		notice.setUpdatedAt(clock.instant());
		SiteNotice saved = notices.save(notice);
		audit.record(AuditRecorder.SITE_NOTICE_UNPUBLISH, AuditRecorder.SITE_NOTICE_ENTITY, id);
		return staffRow(saved);
	}

	/** A soft delete. A draft needs edit rights; a notice that was published or unpublished needs the Master Admin. */
	@Transactional
	public void delete(UUID id) {
		SiteNotice notice = find(id);
		if (SiteNotice.DRAFT.equals(notice.getStatus())) {
			requireEdit();
		}
		else {
			requirePublish();
		}
		Instant now = clock.instant();
		notice.setDeletedAt(now);
		notice.setUpdatedBy(actorId());
		notice.setUpdatedAt(now);
		notices.save(notice);
		audit.record(AuditRecorder.SITE_NOTICE_DELETE, AuditRecorder.SITE_NOTICE_ENTITY, id);
	}

	/** One page of published notices for the public, most recently published first. */
	@Transactional(readOnly = true)
	public PageResponse<PublicRow> published(int page) {
		var request = PageRequest.of(Math.max(page, 0), PUBLIC_PAGE_SIZE);
		var rows = notices.findByStatusAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(SiteNotice.PUBLISHED, request);
		return PageResponse.from(rows.map(SiteNoticeService::publicRow));
	}

	/** One published notice. A draft, an unpublished notice or a deleted one is not found. */
	@Transactional(readOnly = true)
	public PublicRow publicNotice(UUID id) {
		SiteNotice notice = notices.findByIdAndDeletedAtIsNull(id)
			.filter(row -> SiteNotice.PUBLISHED.equals(row.getStatus()))
			.orElseThrow(SiteNoticeService::notFound);
		return publicRow(notice);
	}

	/** The four texts, tidied: blank becomes null, line endings become LF, lengths are limited. */
	private static Texts texts(NoticeForm form, boolean complete) {
		NoticeForm input = form == null ? new NoticeForm(null, null, null, null) : form;
		List<FieldErrorDetail> errors = new ArrayList<>();
		String titleEn = clean("titleEn", input.titleEn(), MAX_TITLE, "The English title can be at most 300 characters.", errors);
		String titleBn = clean("titleBn", input.titleBn(), MAX_TITLE, "The Bangla title can be at most 300 characters.", errors);
		String bodyEn = clean("bodyEn", input.bodyEn(), MAX_BODY, "The English description can be at most 10,000 characters.",
				errors);
		String bodyBn = clean("bodyBn", input.bodyBn(), MAX_BODY, "The Bangla description can be at most 10,000 characters.",
				errors);
		Texts texts = new Texts(titleEn, titleBn, bodyEn, bodyBn);
		if (complete) {
			errors.addAll(texts.missing());
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		return texts;
	}

	private static String clean(String field, String value, int max, String tooLong, List<FieldErrorDetail> errors) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.replace("\r\n", "\n").replace('\r', '\n');
		if (text.codePointCount(0, text.length()) > max) {
			errors.add(new FieldErrorDetail(field, tooLong));
			return null;
		}
		return text;
	}

	private SiteNotice find(UUID id) {
		return notices.findByIdAndDeletedAtIsNull(id).orElseThrow(SiteNoticeService::notFound);
	}

	private void requireEdit() {
		if (!permissions.has(Permissions.SITE_NOTICE_EDIT)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
	}

	private void requirePublish() {
		if (!permissions.has(Permissions.SITE_NOTICE_PUBLISH)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validation(List<FieldErrorDetail> errors) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
	}

	private static StaffRow staffRow(SiteNotice notice) {
		return new StaffRow(notice.getId(), notice.getStatus(), notice.getTitleEn(), notice.getTitleBn(), notice.getBodyEn(),
				notice.getBodyBn(), notice.getCreatedAt(), notice.getUpdatedAt(), notice.getPublishedAt());
	}

	private static PublicRow publicRow(SiteNotice notice) {
		return new PublicRow(notice.getId(), notice.getTitleEn(), notice.getTitleBn(), notice.getBodyEn(), notice.getBodyBn(),
				notice.getPublishedAt());
	}

	/** The request body: the four texts. Any of them may be null or blank on a draft. */
	public record NoticeForm(String titleEn, String titleBn, String bodyEn, String bodyBn) {
	}

	/** A staff row: the four texts as stored (null when blank), the status and the dates. */
	public record StaffRow(UUID id, String status, String titleEn, String titleBn, String bodyEn, String bodyBn,
			Instant createdAt, Instant updatedAt, Instant publishedAt) {
	}

	/** A published notice as the public sees it: no status and no staff names. */
	public record PublicRow(UUID id, String titleEn, String titleBn, String bodyEn, String bodyBn, Instant publishedAt) {
	}

	/** The four texts after tidying. {@link #missing()} names each one that is null. */
	private record Texts(String titleEn, String titleBn, String bodyEn, String bodyBn) {

		List<FieldErrorDetail> missing() {
			List<FieldErrorDetail> errors = new ArrayList<>();
			if (titleEn == null) {
				errors.add(new FieldErrorDetail("titleEn", "Enter the English title."));
			}
			if (titleBn == null) {
				errors.add(new FieldErrorDetail("titleBn", "Enter the Bangla title."));
			}
			if (bodyEn == null) {
				errors.add(new FieldErrorDetail("bodyEn", "Enter the English description."));
			}
			if (bodyBn == null) {
				errors.add(new FieldErrorDetail("bodyBn", "Enter the Bangla description."));
			}
			return errors;
		}

		void applyTo(SiteNotice notice) {
			notice.setTitleEn(titleEn);
			notice.setTitleBn(titleBn);
			notice.setBodyEn(bodyEn);
			notice.setBodyBn(bodyBn);
		}

	}

}
