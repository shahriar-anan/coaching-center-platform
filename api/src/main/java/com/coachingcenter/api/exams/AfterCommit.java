package com.coachingcenter.api.exams;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class AfterCommit {

	private final TransactionTemplate requiresNew;

	AfterCommit(PlatformTransactionManager transactions) {
		TransactionTemplate template = new TransactionTemplate(transactions);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.requiresNew = template;
	}

	void run(Runnable action) {
		Runnable work = () -> requiresNew.executeWithoutResult(status -> action.run());
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					work.run();
				}
			});
			return;
		}
		work.run();
	}

}
