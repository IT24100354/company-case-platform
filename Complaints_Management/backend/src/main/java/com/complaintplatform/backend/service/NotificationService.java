package com.complaintplatform.backend.service;

import com.complaintplatform.backend.model.Notification;
import com.complaintplatform.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepo;

    public NotificationService(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public void createNotification(Long recipientId, String type, String title, String message) {
        createNotification(recipientId, null, type, title, message);
    }

    public void createNotification(Long recipientId, Long complaintId, String type, String title, String message) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setComplaintId(complaintId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setRead(false);
        notificationRepo.save(n);
    }
}
