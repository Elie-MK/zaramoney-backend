package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.NotificationChannel;
import com.zekodnix.zaramoney.domain.enumeration.NotificationStatus;
import com.zekodnix.zaramoney.domain.enumeration.NotificationType;
import com.zekodnix.zaramoney.service.dto.NotificationDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.UserMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationServicePlus {

    private final NotificationService notificationService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final ExpoPushService expoPushService;
    private final UserMapper userMapper;

    public NotificationServicePlus(
        NotificationService notificationService,
        UserDetailsAccountService userDetailsAccountService,
        ExpoPushService expoPushService,
        UserMapper userMapper
    ) {
        this.notificationService = notificationService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.expoPushService = expoPushService;
        this.userMapper = userMapper;
    }

    public void createNotification(TransactionRecordDTO result, User currentUser, TransactionRecordDTO vm) {
        var findUser = userDetailsAccountService.findByUserEmail(currentUser.getEmail()).orElseThrow();
        var notification = new NotificationDTO();
        notification.setSentAt(Instant.now());
        notification.setTransaction(result);
        notification.setMessage("Transaction completed successfully");
        notification.setTitle("Transaction Completed");
        notification.setIsRead(false);
        notification.setChannel(NotificationChannel.PUSH);
        notification.setType(NotificationType.TRANSACTION_ALERT);
        notification.setUser(userMapper.userToUserDTO(currentUser));
        notification.setDeepLink("transaction/" + result.getId());
        notification.setData("Transaction completed successfully");
        notification.setStatus(NotificationStatus.SENT);

        notificationService.save(notification);

        expoPushService.sendPush(
            findUser.getExpoPushToken(),
            "Confirmation de transfert",
            "Nous vous confirmons que votre transfert de " + vm.getSendAmount() + "$ a été effectué avec succès.",
            Map.of("type", "TRANSACTION_ALERT", "transactionId", result.getId(), "deepLink", "transaction/" + result.getId())
        );
    }
}
