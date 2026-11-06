package com.coachingcenter.api.staffpay;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seals the payout numbers that were stored before the key existed (staff pay plan, phase 9). It runs only when
 * {@code app.payout.backfill-enabled} is true, and is switched off again once the verification has passed.
 *
 * <p>
 * Each batch is one transaction. Every sealed value is opened again and compared with the plain value before the batch
 * commits; a mismatch rolls the batch back and stops the run. The statements only fill empty sealed columns, so a second run
 * changes nothing. Logs hold counts, never numbers. The plain columns are not touched.
 */
@Component
@Order(2)
public class PayoutNumberBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PayoutNumberBackfill.class);

	static final int BATCH = 50;

	private static final String PENDING = """
			select id, account_number, routing_number, wallet_number from staff_payout_accounts
			where (account_number is not null and account_number_enc is null)
			   or (routing_number is not null and routing_number_enc is null)
			   or (wallet_number is not null and wallet_number_enc is null)
			order by id
			limit %d
			""".formatted(BATCH);

	private static final String FILL = """
			update staff_payout_accounts
			set account_number_enc = coalesce(account_number_enc, :number),
			    routing_number_enc = coalesce(routing_number_enc, :routing),
			    wallet_number_enc = coalesce(wallet_number_enc, :wallet),
			    key_version = coalesce(key_version, :version)
			where id = :id
			""";

	private final JdbcTemplate jdbc;

	/** The named-parameter template for the fill statement, which uses :names. */
	private final NamedParameterJdbcTemplate named;

	private final PayoutNumberCipher cipher;

	private final boolean enabled;

	private final TransactionTemplate transactions;

	public PayoutNumberBackfill(JdbcTemplate jdbc, PayoutNumberCipher cipher, PlatformTransactionManager transactionManager,
			@Value("${app.payout.backfill-enabled:false}") boolean enabled) {
		this.jdbc = jdbc;
		this.named = new NamedParameterJdbcTemplate(jdbc);
		this.cipher = cipher;
		this.enabled = enabled;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}
		if (!cipher.enabled()) {
			throw new IllegalStateException("The payout backfill needs PAYOUT_ENCRYPTION_KEY.");
		}
		int sealed = 0;
		while (true) {
			int batch = sealBatch();
			if (batch == 0) {
				break;
			}
			sealed += batch;
		}
		log.info("payout backfill sealed {} account row(s); switch app.payout.backfill-enabled off", sealed);
	}

	/** Seals one batch and verifies it. Returns how many rows were sealed; 0 when nothing is pending. */
	int sealBatch() {
		Integer count = transactions.execute(status -> {
			List<Map<String, Object>> rows = jdbc.queryForList(PENDING);
			for (Map<String, Object> row : rows) {
				UUID id = (UUID) row.get("id");
				String number = (String) row.get("account_number");
				String routing = (String) row.get("routing_number");
				String wallet = (String) row.get("wallet_number");
				named.update(FILL, new MapSqlParameterSource()
					.addValue("id", id)
					.addValue("number", cipher.seal(PayoutNumberCipher.ACCOUNT, id, number), Types.BINARY)
					.addValue("routing", cipher.seal(PayoutNumberCipher.ROUTING, id, routing), Types.BINARY)
					.addValue("wallet", cipher.seal(PayoutNumberCipher.WALLET, id, wallet), Types.BINARY)
					.addValue("version", cipher.keyVersion(), Types.SMALLINT));
				verify(id, number, routing, wallet);
			}
			return rows.size();
		});
		return count == null ? 0 : count;
	}

	/** Opens the sealed values of one row and compares them with the plain ones. Throws, and so rolls back, on a mismatch. */
	private void verify(UUID id, String number, String routing, String wallet) {
		PayoutNumberCipher.PayoutNumbers opened = jdbc.queryForObject("""
				select account_number_enc, routing_number_enc, wallet_number_enc, key_version
				from staff_payout_accounts where id = ?
				""", (rs, n) -> new PayoutNumberCipher.PayoutNumbers(
				cipher.open(PayoutNumberCipher.ACCOUNT, id, rs.getBytes("account_number_enc")),
				cipher.open(PayoutNumberCipher.ROUTING, id, rs.getBytes("routing_number_enc")),
				cipher.open(PayoutNumberCipher.WALLET, id, rs.getBytes("wallet_number_enc"))), id);
		if (!same(number, opened.account()) || !same(routing, opened.routing()) || !same(wallet, opened.wallet())) {
			throw new IllegalStateException("A sealed payout number does not match its plain value; the batch was rolled back.");
		}
	}

	private static boolean same(String plain, String opened) {
		return plain == null ? opened == null : plain.equals(opened);
	}

}
