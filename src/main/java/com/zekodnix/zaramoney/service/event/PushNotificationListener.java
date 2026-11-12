package com.zekodnix.zaramoney.service.event;

import com.zekodnix.zaramoney.service.ExpoPushService;
import com.zekodnix.zaramoney.service.NotificationServicePlus;
import com.zekodnix.zaramoney.service.push.PushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.scheduler.Schedulers;

/**
 * Sends pushes only AFTER the transaction that requested them has committed. {@code fallbackExecution}: when published
 * outside a transaction, the push is sent right away. The Expo call is asynchronous (retries included); its outcome
 * is then recorded on a worker thread (blocking JDBC must not run on the HTTP client's event loop):
 * notification PENDING → SENT / FAILED, and a dead token is removed.
 */
@Component
public class PushNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationListener.class);

    private final ExpoPushService expoPushService;
    private final NotificationServicePlus notificationServicePlus;

    public PushNotificationListener(ExpoPushService expoPushService, NotificationServicePlus notificationServicePlus) {
        this.expoPushService = expoPushService;
        this.notificationServicePlus = notificationServicePlus;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushRequested(PushNotificationRequestedEvent event) {
        expoPushService
            .sendPush(event.expoPushToken(), event.title(), event.body(), event.data())
            .defaultIfEmpty(PushResult.failed("no result"))
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                result -> record(event, result),
                error -> record(event, PushResult.failed(error.toString()))
            );
    }

    private void record(PushNotificationRequestedEvent event, PushResult result) {
        try {
            notificationServicePlus.recordPushOutcome(event.notificationId(), event.expoPushToken(), result);
        } catch (RuntimeException e) {
            LOG.error("Could not record the push outcome {} for notification {}", result.outcome(), event.notificationId(), e);
        }
    }
}
