package com.coachingcenter.api.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.users.User;

@Service
public class GoogleAccountService {

	private final GoogleAccountTx tx;

	public GoogleAccountService(GoogleAccountTx tx) {
		this.tx = tx;
	}

	public User resolve(GoogleIdentity identity) {
		try {
			return tx.resolve(identity);
		}
		catch (DataIntegrityViolationException exception) {
			return tx.resolve(identity);
		}
	}

}
