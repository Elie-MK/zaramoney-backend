package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.NotificationChannel;
import com.zekodnix.zaramoney.domain.enumeration.NotificationStatus;
import com.zekodnix.zaramoney.domain.enumeration.NotificationType;
import java.time.Instant;

/**
 * A notification as the app sees it ({@code GET /api/plus/notifications}): exactly the documented fields. Built from
 * the internal {@link NotificationDTO} so internal fields (user, full transaction) can never leak through it.
 */
public record CustomerNotificationDTO(
    Long id,
    String title,
    String message,
    NotificationType type,
    NotificationChannel channel,
    NotificationStatus status,
    Boolean isRead,
    String deepLink,
    Instant createdAt,
    Instant sentAt,
    String data,
    TransactionRef transaction
) {
    /** The related transaction (open it with {@code GET /api/plus/transaction-records/{id}}); null when none. */
    public record TransactionRef(Long id, String transactionReference) {}

    public static CustomerNotificationDTO of(NotificationDTO n) {
        TransactionRecordDTO t = n.getTransaction();
        return new CustomerNotificationDTO(
            n.getId(),
            n.getTitle(),
            n.getMessage(),
            n.getType(),
            n.getChannel(),
            n.getStatus(),
            n.getIsRead(),
            n.getDeepLink(),
            n.getCreatedAt(),
            n.getSentAt(),
            n.getData(),
            t == null ? null : new TransactionRef(t.getId(), t.getTransactionReference())
        );
    }
}
