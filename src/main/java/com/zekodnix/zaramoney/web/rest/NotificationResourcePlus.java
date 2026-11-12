package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.NotificationService;
import com.zekodnix.zaramoney.service.NotificationServicePlus;
import com.zekodnix.zaramoney.service.dto.NotificationDTO;
import com.zekodnix.zaramoney.web.rest.vm.PushTokenVM;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current user's notifications and push token (all scoped to the authenticated user).
 */
@RestController
public class NotificationResourcePlus {

    private final NotificationService notificationService;
    private final NotificationServicePlus notificationServicePlus;

    public NotificationResourcePlus(NotificationService notificationService, NotificationServicePlus notificationServicePlus) {
        this.notificationService = notificationService;
        this.notificationServicePlus = notificationServicePlus;
    }

    @GetMapping("/api/plus/notifications")
    public ResponseEntity<List<NotificationDTO>> getCurrentUserNotifications() {
        return ResponseEntity.ok(notificationService.getCurrentUserNotification());
    }

    /** {@code {"count": 3}} — for the app badge. */
    @GetMapping("/api/plus/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationServicePlus.unreadCount()));
    }

    /** Marks one notification as read (idempotent). 404 {@code notificationnotfound} if it is not the user's. */
    @PutMapping("/api/plus/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable("id") Long id) {
        notificationServicePlus.markRead(id);
        return ResponseEntity.noContent().build();
    }

    /** Marks all the user's notifications as read; {@code {"updated": n}}. */
    @PutMapping("/api/plus/notifications/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("updated", notificationServicePlus.markAllRead()));
    }

    /** Sets the Expo push token after a reinstall / token rotation; {@code null} clears it. 404 {@code noaccount} before onboarding. */
    @PutMapping("/api/plus/push-token")
    public ResponseEntity<Void> updatePushToken(@Valid @RequestBody PushTokenVM body) {
        notificationServicePlus.updatePushToken(body.getExpoPushToken());
        return ResponseEntity.noContent().build();
    }
}
