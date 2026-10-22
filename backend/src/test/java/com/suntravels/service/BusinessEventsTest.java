package com.suntravels.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import static org.mockito.Mockito.*;

class BusinessEventsTest
{
    @Test void successEventsWaitForCommitAndRollbackDoesNotLogSuccess() {
        Logger logger=mock(Logger.class);
        when(logger.isInfoEnabled()).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            BusinessEvents.afterCommit(logger,"reservation_confirmed",7);
            verify(logger,never()).info(anyString(),any(),any());
            TransactionSynchronizationUtils.triggerAfterCommit();
            verify(logger).info("event={} id={}","reservation_confirmed",7);
            clearInvocations(logger);
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            BusinessEvents.afterCommit(logger,"reservation_confirmed",8);
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(logger,never()).info(anyString(),any(),any());
        } finally { TransactionSynchronizationManager.clear(); }
    }
}
