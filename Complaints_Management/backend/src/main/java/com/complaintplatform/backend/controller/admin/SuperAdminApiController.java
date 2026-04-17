package com.complaintplatform.backend.controller.admin;

import com.complaintplatform.backend.model.User;
import com.complaintplatform.backend.repository.UserRepository;
import com.complaintplatform.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminApiController {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.complaintplatform.backend.repository.AnnouncementRepository announcementRepository;

    public SuperAdminApiController(UserRepository userRepository, NotificationService notificationService, com.complaintplatform.backend.repository.AnnouncementRepository announcementRepository) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.announcementRepository = announcementRepository;
    }

    @GetMapping("/announcements")
    public List<com.complaintplatform.backend.model.Announcement> getAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers(@RequestParam(required = false) String category) {
        List<User> users;
        if (category == null || category.equalsIgnoreCase("all")) {
            users = userRepository.findAll();
        } else {
            try {
                User.Role role = User.Role.valueOf(category.toUpperCase());
                users = userRepository.findByRole(role);
            } catch (IllegalArgumentException e) {
                users = new ArrayList<>();
            }
        }
        
        return users.stream().map(u -> Map.of(
            "id", (Object)u.getId(),
            "fullName", (Object)u.getFullName(),
            "role", (Object)u.getRole().name()
        )).collect(Collectors.toList());
    }

    @PostMapping("/announcements")
    public ResponseEntity<?> sendAnnouncement(@RequestBody AnnouncementRequest req) {
        List<Long> recipients = new ArrayList<>();
        
        if (req.isAll()) {
            if (req.getCategory().equalsIgnoreCase("all")) {
                recipients = userRepository.findAll().stream().map(User::getId).collect(Collectors.toList());
            } else {
                try {
                    User.Role role = User.Role.valueOf(req.getCategory().toUpperCase());
                    recipients = userRepository.findByRole(role).stream().map(User::getId).collect(Collectors.toList());
                } catch (IllegalArgumentException e) {
                    // Ignore or handle
                }
            }
        } else if (req.getUserIds() != null) {
            recipients = req.getUserIds();
        }

        for (Long userId : recipients) {
            notificationService.createNotification(userId, "ANNOUNCEMENT", req.getTitle(), req.getDescription());
        }

        // Save a record of the announcement
        com.complaintplatform.backend.model.Announcement announcement = new com.complaintplatform.backend.model.Announcement();
        announcement.setTitle(req.getTitle());
        announcement.setMessage(req.getDescription());
        announcement.setTargetAudience(req.isAll() ? "All " + req.getCategory() : "Selected Users");
        announcement.setRecipientsCount(recipients.size());
        announcementRepository.save(announcement);

        return ResponseEntity.ok().body(Map.of("message", "send successfully"));
    }

    public static class AnnouncementRequest {
        private String category;
        private boolean all;
        private List<Long> userIds;
        private String title;
        private String description;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public boolean isAll() { return all; }
        public void setAll(boolean all) { this.all = all; }
        public List<Long> getUserIds() { return userIds; }
        public void setUserIds(List<Long> userIds) { this.userIds = userIds; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
