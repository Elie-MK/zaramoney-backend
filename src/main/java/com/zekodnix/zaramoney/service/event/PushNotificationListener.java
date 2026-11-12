package com.zekodnix.zaramoney.service.event;

import com.zekodnix.zaramoney.service.ExpoPushService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends pushes only AFTER the transaction that requested them has committed. {@code fallbackExecution}: when published
 * outside a transaction, the push is sent right away. The Expo call itself is asynchronous (WebClient subscribe).
 */
@Component
public class PushNotificationListener {

    private final ExpoPushService expoPushService;

    public PushNotificationListener(ExpoPushService expoPushService) {
        this.expoPushService = expoPushService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushRequested(PushNotificationRequestedEvent event) {
        expoPushService.sendPush(event.expoPushToken(), event.title(), event.body(), event.data());
    }
}
