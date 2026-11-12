package com.zekodnix.zaramoney.service.event;

import java.util.Map;

/**
 * A push to send once the current transaction has committed (see {@code PushNotificationListener}).
 * Published instead of calling Expo directly, so a transaction that rolls back never notifies anyone.
 * {@code notificationId}: the stored notification whose delivery status (PENDING → SENT / FAILED) is updated; may be null.
 */
public record PushNotificationRequestedEvent(
    Long notificationId,
    String expoPushToken,
    String title,
    String body,
    Map<String, Object> data
) {}
