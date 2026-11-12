package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.NotificationRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import com.zekodnix.zaramoney.domain.enumeration.NotificationChannel;
import com.zekodnix.zaramoney.domain.enumeration.NotificationStatus;
import com.zekodnix.zaramoney.domain.enumeration.NotificationType;
import com.zekodnix.zaramoney.service.dto.NotificationDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.event.PushNotificationRequestedEvent;
import com.zekodnix.zaramoney.service.push.PushResult;
import com.zekodnix.zaramoney.service.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServicePlus {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationServicePlus.class);

    /** App screen showing the wallet / KYC state ({@code GET /api/user-account}). */
    static final String KYC_DEEP_LINK = "account";

    private final NotificationService notificationService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final NotificationRepository notificationRepository;
    private final UserDetailsAccountRepository userDetailsAccountRepository;
    private final UserRepository userRepository;

    public NotificationServicePlus(
        NotificationService notificationService,
        UserDetailsAccountService userDetailsAccountService,
        ApplicationEventPublisher eventPublisher,
        UserMapper userMapper,
        NotificationRepository notificationRepository,
        UserDetailsAccountRepository userDetailsAccountRepository,
        UserRepository userRepository
    ) {
        this.notificationService = notificationService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.eventPublisher = eventPublisher;
        this.userMapper = userMapper;
        this.notificationRepository = notificationRepository;
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.userRepository = userRepository;
    }

    // ---------------------------------------------------------------------------------------------- app endpoints

    /** Marks one of the current user's notifications as read (idempotent). 404 if it is not theirs. */
    @Transactional
    public void markRead(Long notificationId) {
        Long userId = currentUserId();
        if (!notificationRepository.existsByIdAndUser_Id(notificationId, userId)) {
            throw ApiException.notFound("Notification not found", "notificationnotfound");
        }
        notificationRepository.markRead(notificationId, userId);
    }

    /** @return how many notifications were unread and are now read */
    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(currentUserId());
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUser_IdAndIsReadFalse(currentUserId());
    }

    /**
     * Sets (or clears, with null) the current user's Expo push token. A token identifies a device, so it is removed
     * from any other user first: after an account switch on a phone, the previous user's pushes stop going there.
     */
    @Transactional
    public void updatePushToken(String expoPushToken) {
        Long userId = currentUserId();
        UserDetailsAccount details = userDetailsAccountRepository
            .findOneByUser_Id(userId)
            .orElseThrow(() -> ApiException.notFound("Complete the onboarding first", "noaccount"));
        String token = expoPushToken == null || expoPushToken.isBlank() ? null : expoPushToken.trim();
        if (token != null) {
            int released = userDetailsAccountRepository.releasePushTokenFromOtherUsers(token, userId);
            if (released > 0) {
                LOG.info("Push token moved to user {} (removed from {} other account(s))", userId, released);
            }
        }
        details = userDetailsAccountRepository.findById(details.getId()).orElseThrow(); // reload after the bulk update
        details.setExpoPushToken(token);
        userDetailsAccountRepository.save(details);
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .map(User::getId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));
    }

    @Transactional
    public void createTransactionNotification(TransactionRecordDTO result, User user, BigDecimal amount, boolean isSender) {
        String title = "Confirmation de transaction";
        String message = isSender
            ? "Votre transfert de " + amount + "$ a été effectué avec succès."
            : "Vous avez reçu " + amount + "$ avec succès.";
        String token = userDetailsAccountService
            .findByUserEmail(user.getEmail())
            .map(details -> details.getExpoPushToken())
            .orElse(null);

        var notification = newNotification(user, title, message, NotificationType.TRANSACTION_ALERT, "transaction/" + result.getId());
        notification.setTransaction(result);
        notification.setData(message);
        notifyUser(
            notification,
            token,
            Map.of("type", "TRANSACTION_ALERT", "transactionId", result.getId(), "deepLink", "transaction/" + result.getId())
        );
    }

    /**
     * KYC decision (VERIFIED / REJECTED): stored notification + push after commit. The push data lets the app refresh
     * {@code GET /api/user-account}: {@code {"type":"KYC_STATUS","kycStatus":"VERIFIED","deepLink":"account"}}.
     * Must run inside the caller's transaction (the push is published for AFTER_COMMIT).
     */
    @Transactional
    public void createKycStatusNotification(UserDetailsAccount details, KycStatus status) {
        User user = details.getUser();
        if (user == null) {
            LOG.warn("KYC notification skipped: userDetails {} has no user", details.getId());
            return;
        }
        String title;
        String message;
        switch (status) {
            case VERIFIED -> {
                title = "Compte vérifié";
                message = "Votre identité a été vérifiée. Vous pouvez maintenant envoyer et retirer de l'argent.";
            }
            case REJECTED -> {
                title = "Vérification refusée";
                message = "Votre vérification d'identité a été refusée. Veuillez contacter le support ZaraMoney.";
            }
            default -> {
                return; // only decisions are notified
            }
        }
        var notification = newNotification(user, title, message, NotificationType.SYSTEM, KYC_DEEP_LINK);
        notification.setData("{\"type\":\"KYC_STATUS\",\"kycStatus\":\"" + status + "\"}");
        notifyUser(notification, details.getExpoPushToken(), Map.of("type", "KYC_STATUS", "kycStatus", status.name(), "deepLink", KYC_DEEP_LINK));
    }

    /**
     * Records the delivery outcome of a push (called by {@code PushNotificationListener}, after commit, in its own
     * transaction): the notification becomes SENT or FAILED (FAILED = dead letter, queryable by admins), and a token
     * Expo reports as DeviceNotRegistered (app uninstalled) is removed so we stop sending to it.
     */
    @Transactional
    public void recordPushOutcome(Long notificationId, String token, PushResult result) {
        if (notificationId != null) {
            notificationRepository.updateDeliveryStatus(
                notificationId,
                result.delivered() ? NotificationStatus.SENT : NotificationStatus.FAILED,
                result.delivered() ? Instant.now() : null
            );
        }
        if (result.outcome() == PushResult.Outcome.INVALID_TOKEN && token != null) {
            int cleared = userDetailsAccountRepository.clearPushToken(token);
            LOG.info("Removed an unregistered Expo push token from {} account(s)", cleared);
        }
    }

    private NotificationDTO newNotification(User user, String title, String message, NotificationType type, String deepLink) {
        var notification = new NotificationDTO();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setChannel(NotificationChannel.PUSH);
        notification.setType(type);
        notification.setUser(userMapper.userToUserDTO(user));
        notification.setDeepLink(deepLink);
        return notification;
    }

    /**
     * Stores the notification (always visible in the app list) and queues its push for after commit.
     * Status: PENDING until Expo answers (then SENT / FAILED); FAILED right away when the user has no push token.
     */
    private void notifyUser(NotificationDTO notification, String token, Map<String, Object> pushData) {
        boolean hasToken = token != null && !token.isBlank();
        notification.setStatus(hasToken ? NotificationStatus.PENDING : NotificationStatus.FAILED);
        NotificationDTO saved = notificationService.save(notification);
        if (hasToken) {
            // The push goes out only after commit (PushNotificationListener): never for a transaction that rolls back
            eventPublisher.publishEvent(
                new PushNotificationRequestedEvent(saved.getId(), token, notification.getTitle(), notification.getMessage(), pushData)
            );
        }
    }

    public List<NotificationDTO> getCurrentUserNotifications() {
        LOG.debug("Retrieving current user notifications");
        return notificationService.getCurrentUserNotification();
    }
}
