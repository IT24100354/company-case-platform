package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.*;
import com.complaintplatform.backend.repository.*;
import com.complaintplatform.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/resolutions")
public class ResolutionApiController {

    private final ResolutionRepository resolutionRepo;
    private final ComplaintRepository complaintRepo;
    private final UserRepository userRepo;
    private final NotificationService notifService;

    @Value("${cms.upload.path}")
    private String uploadDir;

    public ResolutionApiController(ResolutionRepository resolutionRepo, ComplaintRepository complaintRepo, 
                                   UserRepository userRepo, NotificationService notifService) {
        this.resolutionRepo = resolutionRepo;
        this.complaintRepo = complaintRepo;
        this.userRepo = userRepo;
        this.notifService = notifService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> submit(
            @RequestParam("complaintId") Long complaintId,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        try {
            Complaint complaint = complaintRepo.findById(complaintId).orElseThrow();
            
            Resolution res = new Resolution();
            res.setComplaintId(complaintId);
            res.setTitle(title);
            res.setDescription(description);
            res.setSubmittedById(userId);
            res.setSubmittedAt(LocalDateTime.now());

            if (file != null && !file.isEmpty()) {
                Path root = Paths.get(uploadDir);
                if (!Files.exists(root)) Files.createDirectories(root);
                String fileName = "res_" + complaintId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Files.copy(file.getInputStream(), root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                res.setDocumentPath("/uploads/" + fileName);
                res.setDocumentName(file.getOriginalFilename());
            }

            resolutionRepo.save(res);

            // Update Complaint Status
            complaint.setStatus(Complaint.Status.RESOLUTION_SENT);
            complaintRepo.save(complaint);

            // Notify Admins
            String msg = "A resolution has been submitted for: " + complaint.getTitle();
            if (complaint.getAssignedAdminId() != null) {
                notifService.createNotification(complaint.getAssignedAdminId(), complaintId, "RESOLUTION_RECEIVED", "Resolution Submitted", msg);
            }
            userRepo.findByRole(User.Role.SUPER_ADMIN).stream().findFirst().ifPresent(sa -> {
                notifService.createNotification(sa.getId(), complaintId, "RESOLUTION_RECEIVED", "Resolution Submitted", msg);
            });

            // Notify Company Admin
            userRepo.findAll().stream()
                    .filter(u -> u.getRole() == User.Role.COMPANY_ADMIN && u.getCompanyId().equals(complaint.getCompanyId()))
                    .findFirst()
                    .ifPresent(ca -> {
                        notifService.createNotification(ca.getId(), complaintId, "RESOLUTION_SENT", "Resolution Sent", "Department submitted a resolution for: " + complaint.getTitle());
                    });

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/complaint/{id}")
    public ResponseEntity<?> getByComplaint(@PathVariable Long id) {
        return ResponseEntity.ok(resolutionRepo.findAll().stream()
                .filter(r -> r.getComplaintId().equals(id))
                .sorted(Comparator.comparing(Resolution::getSubmittedAt).reversed())
                .toList());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestParam Long userId) {
        Complaint complaint = complaintRepo.findById(id).orElseThrow();
        complaint.setStatus(Complaint.Status.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.now());
        complaintRepo.save(complaint);
        notifyAll(complaint, "Case Resolved", "The resolution for '" + complaint.getTitle() + "' has been approved.");
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestParam Long userId, @RequestBody Map<String, String> body) {
        Complaint complaint = complaintRepo.findById(id).orElseThrow();
        String reason = body.getOrDefault("reason", "Resolution rejected.");
        complaint.setStatus(Complaint.Status.RESOLUTION_REJECTED);
        complaintRepo.save(complaint);
        notifyAll(complaint, "Resolution Rejected", "The resolution for '" + complaint.getTitle() + "' was rejected. Reason: " + reason);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void notifyAll(Complaint c, String title, String msg) {
        notifService.createNotification(c.getComplainantId(), c.getId(), "STATUS_UPDATE", title, msg);
        userRepo.findAll().stream()
                .filter(u -> u.getRole() == User.Role.COMPANY_ADMIN && u.getCompanyId().equals(c.getCompanyId()))
                .findFirst().ifPresent(ca -> notifService.createNotification(ca.getId(), c.getId(), "STATUS_UPDATE", title, msg));
        if (c.getDepartmentUserId() != null) {
            notifService.createNotification(c.getDepartmentUserId(), c.getId(), "STATUS_UPDATE", title, msg);
        }
    }
}
