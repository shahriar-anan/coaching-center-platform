package com.coachingcenter.api.offline;

import java.security.SecureRandom;
import java.util.List;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.EmailSender;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.SecretHasher;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.mail.EmailTemplates;
import com.coachingcenter.api.common.mail.MailContext;
import com.coachingcenter.api.common.mail.MailRecipient;
import com.coachingcenter.api.common.mail.RenderedEmail;

/**
 * The mails of the public offline application (public-admission plan, section 7) and the private tokens behind them. The mails go to an
 * address, not to an account, so they are rendered here with the {@link EmailTemplates} helpers and handed to the sender, which sends
 * them after the caller's transaction commits. Tokens are random, and only their SHA-256 is stored (V94).
 */
@Component
public class OfflineApplicationMail {

	/** A reopen link and a public application's token are random 32 bytes, shown as 64 hex characters. */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** How long a join or activation code, and the reopen link, last (P8, Q4). */
	public static final int CODE_DAYS = 7;

	private final EmailSender email;

	private final AppProperties properties;

	private final SecretHasher hasher;

	public OfflineApplicationMail(EmailSender email, AppProperties properties, SecretHasher hasher) {
		this.email = email;
		this.properties = properties;
		this.hasher = hasher;
	}

	/** A new random token, for the caller to keep (in a link or the browser) and store only as {@link #hash}. */
	public static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	/** A join code: eight characters from an alphabet with no look-alikes, as the activation code is made (V94). */
	public static String newCode() {
		char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
		char[] value = new char[8];
		for (int index = 0; index < value.length; index++) {
			value[index] = alphabet[RANDOM.nextInt(alphabet.length)];
		}
		return new String(value);
	}

	/** The stored form of a token. */
	public String hash(String token) {
		return hasher.sha256(token);
	}

	/** The private link that reopens a changes-requested form (Q4). */
	public String reopenLink(String token) {
		return MailContext.from(properties).site("/admission/reopen/" + token);
	}

	public void applicationReceived(String to, String name, String batchBn, String batchEn) {
		MailContext ctx = MailContext.from(properties);
		RenderedEmail mail = EmailTemplates.offlineApplicationReceived(ctx, recipient(to, name), batchBn, batchEn);
		email.sendRendered(to, mail, null);
	}

	public void changesRequested(String to, String name, String reason, String batchBn, String batchEn, String reopenLink) {
		MailContext ctx = MailContext.from(properties);
		RenderedEmail mail = EmailTemplates.offlineChangesRequested(ctx, recipient(to, name), reason, batchBn, batchEn, reopenLink);
		// The link is private: SMTP redacts it from failure logs, and the dev logger shows it when codes are exposed.
		email.sendRendered(to, mail, reopenLink);
	}

	/** A private link back to an open application, for an applicant who started it again with the same email (F7). */
	public void resume(String to, String name, String batchBn, String batchEn, String link) {
		MailContext ctx = MailContext.from(properties);
		RenderedEmail mail = EmailTemplates.offlineResume(ctx, recipient(to, name), batchBn, batchEn, link);
		email.sendRendered(to, mail, link);
	}

	/** The activation code of an applicant who got an account (no match). {@code resent} is true for a new code (P8). */
	public void activation(String to, String name, String batchBn, String batchEn, String code, boolean resent) {
		MailContext ctx = MailContext.from(properties);
		RenderedEmail mail = EmailTemplates.offlineActivation(ctx, recipient(to, name), batchBn, batchEn, code, CODE_DAYS, resent);
		email.sendRendered(to, mail, code);
	}

	/** The join code of an account holder who is admitted (a match, or an applicant who signed in). */
	public void join(String to, String name, String batchBn, String batchEn, List<String[]> allBatches, String code, boolean resent) {
		MailContext ctx = MailContext.from(properties);
		RenderedEmail mail = EmailTemplates.offlineJoin(ctx, recipient(to, name), batchBn, batchEn, allBatches, code, CODE_DAYS, resent);
		email.sendRendered(to, mail, code);
	}

	private static MailRecipient recipient(String to, String name) {
		return new MailRecipient(to, name, Role.STUDENT);
	}

}
