package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.Notification;
import com.complaintplatform.backend.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationRepository notificationRepo;

    public NotificationApiController(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(@RequestParam("userId") Long userId) {
        return ResponseEntity.ok(notificationRepo.findByRecipientIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        notificationRepo.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepo.save(n);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(@RequestParam("userId") Long userId) {
        List<Notification> unread = notificationRepo.findByRecipientIdAndReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepo.saveAll(unread);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
