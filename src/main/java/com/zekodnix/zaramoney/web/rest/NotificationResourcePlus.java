package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.NotificationService;
import com.zekodnix.zaramoney.service.dto.NotificationDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.Notification}.
 */
@RestController
@RequestMapping("/api/plus/notifications")
public class NotificationResourcePlus {

    private final NotificationService notificationService;

    public NotificationResourcePlus(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity< List<NotificationDTO> >getCurrentUserNotifications() {
       var res = notificationService.getCurrentUserNotification();
       return ResponseEntity.ok(res);
    }


}
