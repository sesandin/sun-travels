package com.suntravels.service;

import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Never report success before the database commits. IDs only, no customer or financial data. */
final class BusinessEvents
{
    private BusinessEvents() {}
    static void afterCommit(Logger logger, String event, int id)
    {
        if (!logger.isInfoEnabled()) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { logger.info("event={} id={}", event, id); }
            });
    }
}
