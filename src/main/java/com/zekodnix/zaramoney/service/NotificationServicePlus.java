package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.NotificationChannel;
import com.zekodnix.zaramoney.domain.enumeration.NotificationStatus;
import com.zekodnix.zaramoney.domain.enumeration.NotificationType;
import com.zekodnix.zaramoney.service.dto.NotificationDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.event.PushNotificationRequestedEvent;
import com.zekodnix.zaramoney.service.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServicePlus {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationServicePlus.class);

    private final NotificationService notificationService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    public NotificationServicePlus(
        NotificationService notificationService,
        UserDetailsAccountService userDetailsAccountService,
        ApplicationEventPublisher eventPublisher,
        UserMapper userMapper
    ) {
        this.notificationService = notificationService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.eventPublisher = eventPublisher;
        this.userMapper = userMapper;
    }

    @Transactional
    public void createTransactionNotification(TransactionRecordDTO result, User user, BigDecimal amount, boolean isSender) {
        String title = "Confirmation de transaction";
        String message = isSender
            ? "Votre transfert de " + amount + "$ a été effectué avec succès."
            : "Vous avez reçu " + amount + "$ avec succès.";

        saveNotification(result, user, title, message);

        // The push goes out only after commit (PushNotificationListener): never for a transaction that rolls back
        userDetailsAccountService
            .findByUserEmail(user.getEmail())
            .map(details -> details.getExpoPushToken())
            .filter(token -> token != null && !token.isBlank())
            .ifPresent(token ->
                eventPublisher.publishEvent(
                    new PushNotificationRequestedEvent(
                        token,
                        title,
                        message,
                        Map.of("type", "TRANSACTION_ALERT", "transactionId", result.getId(), "deepLink", "transaction/" + result.getId())
                    )
                )
            );
    }

    private void saveNotification(TransactionRecordDTO result, User user, String title, String message) {
        var notification = new NotificationDTO();
        notification.setSentAt(Instant.now());
        notification.setTransaction(result);
        notification.setMessage(message);
        notification.setTitle(title);
        notification.setIsRead(false);
        notification.setChannel(NotificationChannel.PUSH);
        notification.setType(NotificationType.TRANSACTION_ALERT);
        notification.setUser(userMapper.userToUserDTO(user));
        notification.setDeepLink("transaction/" + result.getId());
        notification.setData(message);
        notification.setStatus(NotificationStatus.SENT);

        notificationService.save(notification);
    }

    public List<NotificationDTO> getCurrentUserNotifications() {
        LOG.debug("Retrieving current user notifications");
        return notificationService.getCurrentUserNotification();
    }
}
