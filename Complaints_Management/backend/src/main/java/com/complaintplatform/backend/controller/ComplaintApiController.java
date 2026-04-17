package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.*;
import com.complaintplatform.backend.repository.*;
import com.complaintplatform.backend.service.ComplaintService;
import com.complaintplatform.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping({"/api/complaints", "/api/admin/complaints"})
public class ComplaintApiController {

    private final ComplaintService complaintService;
    private final ComplaintRepository complaintRepo;
    private final UserRepository userRepo;
    private final NotificationService notifService;
    private final ResolutionRepository resolutionRepo;
    private final EvidenceRepository evidenceRepo;
    private final ChatMessageRepository chatRepo;

    @Value("${cms.upload.path}")
    private String uploadDir;

    public ComplaintApiController(ComplaintService complaintService, ComplaintRepository complaintRepo,
                                   UserRepository userRepo, NotificationService notifService,
                                   ResolutionRepository resolutionRepo, EvidenceRepository evidenceRepo,
                                   ChatMessageRepository chatRepo) {
        this.complaintService = complaintService;
        this.complaintRepo = complaintRepo;
        this.userRepo = userRepo;
        this.notifService = notifService;
        this.resolutionRepo = resolutionRepo;
        this.evidenceRepo = evidenceRepo;
        this.chatRepo = chatRepo;
    }

    @PostMapping(value = "/submit", consumes = { "multipart/form-data" })
    public ResponseEntity<?> submit(
            @RequestParam("userId") Long userId,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "companyId", required = false) String companyIdStr,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "documentFile", required = false) MultipartFile documentFile,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(value = "voiceFile", required = false) MultipartFile voiceFile) {

        System.out.println("DEBUG: Submission received from userId: " + userId + " - Title: " + title);
        
        try {
            Long companyId = null;
            if (companyIdStr != null && !companyIdStr.isBlank() && !companyIdStr.equals("undefined")) {
                companyId = Long.parseLong(companyIdStr);
            }

            final User user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
            
            Complaint c = new Complaint();
            c.setTitle(title);
            c.setDescription(description);
            c.setCategory(category != null ? category : "GENERAL");
            c.setComplainantId(userId);
            c.setComplainantName(user.getFullName());
            c.setComplainantType(user.getRole().name());
            c.setCompanyId(companyId);
            c.setCompanyName(companyName);
            c.setDepartment(department);
            c.setStatus(Complaint.Status.PENDING);
            c.setCreatedAt(LocalDateTime.now());
            
            final Complaint saved = complaintRepo.save(c);

            // Process Files
            saveEvidence(saved, userId, documentFile, "DOCUMENT");
            saveEvidence(saved, userId, videoFile, "VIDEO");
            saveEvidence(saved, userId, voiceFile, "VOICE");

            // Notify Super Admin
            userRepo.findByRole(User.Role.SUPER_ADMIN).stream().findFirst().ifPresent(sa -> {
                notifService.createNotification(sa.getId(), saved.getId(), "NEW_COMPLAINT", "New Complaint Filed", 
                    "User " + user.getFullName() + " submitted: " + title);
            });

            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Backend error: " + e.getMessage()));
        }
    }

    private void saveEvidence(Complaint complaint, Long userId, MultipartFile file, String type) throws IOException {
        if (file == null || file.isEmpty()) return;

        Path root = Paths.get(uploadDir);
        if (!Files.exists(root)) Files.createDirectories(root);
        
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String fileName = type.toLowerCase() + "_" + complaint.getId() + "_" + System.currentTimeMillis() + ext;
        
        Files.copy(file.getInputStream(), root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        Evidence ev = new Evidence();
        ev.setCaseId(String.valueOf(complaint.getId()));
        ev.setUserId(String.valueOf(userId));
        ev.setFileName(originalName);
        ev.setFilePath("/uploads/" + fileName);
        ev.setFileType(type);
        evidenceRepo.save(ev);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestParam("userId") Long userId,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "documentFile", required = false) MultipartFile documentFile,
            @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(value = "voiceFile", required = false) MultipartFile voiceFile) {

        System.out.println("DEBUG: Update request for ID: " + id + " by userId: " + userId);
        
        try {
            Complaint c = complaintRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Complaint not found"));

            System.out.println("DEBUG: Comparing Complaint ComplainantId: " + c.getComplainantId() + " with request userId: " + userId);

            User actor = userRepo.findById(userId).orElseThrow();
            if (actor.getRole() != User.Role.SUPER_ADMIN && (c.getComplainantId() == null || !c.getComplainantId().equals(userId))) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied. Complaint owner ID " + c.getComplainantId() + " does not match your ID " + userId));
            }

            if (c.getStatus() != Complaint.Status.PENDING) {
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "Only PENDING complaints can be edited."));
            }

            c.setTitle(title);
            c.setDescription(description);
            if (category != null) c.setCategory(category);
            
            final Complaint saved = complaintRepo.save(c);

            // Process Files
            saveEvidence(saved, userId, documentFile, "DOCUMENT");
            saveEvidence(saved, userId, videoFile, "VIDEO");
            saveEvidence(saved, userId, voiceFile, "VOICE");

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Update error: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "status", required = false) String status) {

        if (userId == null) {
            // Only Super Admin should call without userId generally, but for safety returning empty if not SA
            return ResponseEntity.ok(Collections.emptyList());
        }

        Optional<User> userOpt = userRepo.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("message", "User session invalid. Please log in again."));
        }

        User user = userOpt.get();
        List<Complaint> complaints = new ArrayList<>();

        switch (user.getRole()) {
            case SUPER_ADMIN:
                if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                    try {
                        complaints = complaintRepo.findByStatus(Complaint.Status.valueOf(status.toUpperCase()));
                    } catch (Exception e) {
                        complaints = complaintRepo.findAllByOrderByCreatedAtDesc();
                    }
                } else {
                    complaints = complaintRepo.findAllByOrderByCreatedAtDesc();
                }
                break;
            case ADMIN:
                complaints = complaintRepo.findByAssignedAdminId(userId);
                break;
            case COMPANY_ADMIN:
                complaints = complaintRepo.findByCompanyId(user.getCompanyId());
                break;
            case DEPT_USER:
                complaints = complaintRepo.findByDepartmentUserId(userId);
                break;
            default:
                complaints = complaintRepo.findByComplainantId(userId);
        }
        return ResponseEntity.ok(complaints.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription());
            map.put("category", c.getCategory());
            map.put("complainantType", c.getComplainantType());
            map.put("complainantId", c.getComplainantId());
            map.put("complainantName", c.getComplainantName());
            map.put("companyId", c.getCompanyId());
            map.put("companyName", c.getCompanyName());
            map.put("department", c.getDepartment());
            map.put("createdAt", c.getCreatedAt());
            map.put("submittedAt", c.getCreatedAt());
            map.put("dueDate", c.getDueDate());
            map.put("priority", c.getPriority());
            
            // Logic for visible status
            String statusStr = c.getStatus().name();
            if (c.getStatus() == Complaint.Status.FORWARDED) {
                // If it was forwarded to a department already, other roles see it as IN_PROGRESS
                if (c.getDepartmentUserId() != null && user.getRole() != User.Role.COMPANY_ADMIN) {
                    statusStr = "IN_PROGRESS";
                }
            }
            map.put("status", statusStr);
            return map;
        }).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id, @RequestParam("userId") Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        return complaintRepo.findById(id)
                .map(c -> {
                    if (user.getRole() == User.Role.ADMIN) {
                        if (c.getAssignedAdminId() == null || !c.getAssignedAdminId().equals(userId)) {
                            return ResponseEntity.status(403).body(Map.of("error", "Access denied. Not assigned to you."));
                        }
                    } else if (user.getRole() == User.Role.COMPANY_ADMIN) {
                        if (!c.getCompanyId().equals(user.getCompanyId())) {
                             return ResponseEntity.status(403).body(Map.of("error", "Access denied for this company."));
                        }
                        // Status Update: VIEWED
                        if (c.getStatus() == Complaint.Status.APPROVED || c.getStatus() == Complaint.Status.FORWARDED) {
                            c.setStatus(Complaint.Status.VIEWED);
                            c.setViewedByCompanyAt(LocalDateTime.now());
                            complaintRepo.save(c);
                        }
                    } else if (user.getRole() == User.Role.DEPT_USER) {
                        if (c.getDepartmentUserId() == null || !c.getDepartmentUserId().equals(userId)) {
                            return ResponseEntity.status(403).body(Map.of("error", "Access denied for this department."));
                        }
                        // Status Update: IN_PROGRESS
                        if (c.getStatus() == Complaint.Status.VIEWED || c.getStatus() == Complaint.Status.FORWARDED) {
                            c.setStatus(Complaint.Status.IN_PROGRESS);
                            complaintRepo.save(c);
                        }
                    }
                    String statusStr = c.getStatus().name();
                    if (c.getStatus() == Complaint.Status.FORWARDED) {
                        if (c.getDepartmentUserId() != null && user.getRole() != User.Role.COMPANY_ADMIN) {
                            statusStr = "IN_PROGRESS";
                        }
                    }
                    
                    Map<String, Object> map = new HashMap<>(); // Using HashMap to avoid modifying original Entity if possible, but simpler to just return a Map
                    map.put("id", c.getId()); map.put("title", c.getTitle()); map.put("description", c.getDescription());
                    map.put("category", c.getCategory()); map.put("complainantType", c.getComplainantType());
                    map.put("status", statusStr); 
                    map.put("createdAt", c.getCreatedAt());
                    map.put("submittedAt", c.getCreatedAt());
                    map.put("dueDate", c.getDueDate());
                    map.put("companyId", c.getCompanyId()); map.put("companyName", c.getCompanyName());
                    map.put("department", c.getDepartment()); map.put("complainantName", c.getComplainantName());
                    map.put("priority", c.getPriority());
                    
                    // Add other fields that might be needed by detail views
                    
                    return ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/resolution")
    public ResponseEntity<?> getResolution(@PathVariable Long id) {
        return resolutionRepo.findAll().stream()
                .filter(r -> r.getComplaintId().equals(id))
                .max(Comparator.comparing(Resolution::getSubmittedAt))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncComplaint(@RequestBody Complaint complaint) {
        if (complaint.getId() != null && complaintRepo.existsById(complaint.getId())) {
             Complaint existing = complaintRepo.findById(complaint.getId()).get();
             existing.setStatus(complaint.getStatus());
             existing.setPriority(complaint.getPriority());
             existing.setAssignedAdminId(complaint.getAssignedAdminId());
             return ResponseEntity.ok(complaintRepo.save(existing));
        }
        return ResponseEntity.ok(complaintRepo.save(complaint));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestParam("userId") Long userId) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        User actor = userRepo.findById(userId).orElseThrow();
        
        if (actor.getRole() == User.Role.ADMIN && (c.getAssignedAdminId() == null || !c.getAssignedAdminId().equals(userId))) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }

        Complaint saved = complaintService.approve(id);

        // Notify party who didn't approve
        if (actor.getRole() == User.Role.SUPER_ADMIN) {
            if (c.getAssignedAdminId() != null) {
                notifService.createNotification(c.getAssignedAdminId(), saved.getId(), "APPROVAL", "Complaint Approved", 
                    "Super Admin approved: " + c.getTitle());
            }
        } else if (actor.getRole() == User.Role.ADMIN) {
            userRepo.findByRole(User.Role.SUPER_ADMIN).stream().findFirst().ifPresent(sa -> {
                notifService.createNotification(sa.getId(), saved.getId(), "APPROVAL", "Complaint Approved", 
                    "Admin " + actor.getFullName() + " approved: " + c.getTitle());
            });
        }

        // Notify Complainant
        notifService.createNotification(c.getComplainantId(), saved.getId(), "STATUS_UPDATE", "Complaint Approved", 
            "Your complaint '" + c.getTitle() + "' has been approved.");

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestParam("userId") Long userId, @RequestBody Map<String, String> body) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        User user = userRepo.findById(userId).orElseThrow();
        if (user.getRole() == User.Role.ADMIN && (c.getAssignedAdminId() == null || !c.getAssignedAdminId().equals(userId))) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }
        String reason = body.getOrDefault("reason", "No reason provided");
        return ResponseEntity.ok(complaintService.reject(id, reason));
    }

    @PostMapping("/{id}/forward")
    public ResponseEntity<?> forward(@PathVariable Long id, @RequestParam("userId") Long userId, @RequestBody Map<String, String> body) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        User user = userRepo.findById(userId).orElseThrow();
        if (user.getRole() == User.Role.ADMIN && (c.getAssignedAdminId() == null || !c.getAssignedAdminId().equals(userId))) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }
        String priority = body.get("priority");
        return ResponseEntity.ok(complaintService.forward(id, priority));
    }

    @PostMapping("/{id}/forward-to-dept")
    public ResponseEntity<?> forwardToDept(@PathVariable Long id, @RequestParam("userId") Long userId, @RequestBody Map<String, Object> body) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        User user = userRepo.findById(userId).orElseThrow();
        
        if (user.getRole() != User.Role.COMPANY_ADMIN || !c.getCompanyId().equals(user.getCompanyId())) {
             return ResponseEntity.status(403).body(Map.of("error", "Access denied. Only company admin can forward to department."));
        }

        Long deptUserId = body.get("deptUserId") instanceof Number ? ((Number)body.get("deptUserId")).longValue() : Long.parseLong(body.get("deptUserId").toString());
        String deptName = (String) body.get("deptName");
        
        c.setDepartmentUserId(deptUserId);
        c.setDepartment(deptName);
        c.setStatus(Complaint.Status.FORWARDED);
        c.setSentToDepartmentAt(LocalDateTime.now());
        Complaint saved = complaintRepo.save(c);
        
        // Notify Dept User
        notifService.createNotification(deptUserId, saved.getId(), "NEW_ASSIGNMENT", "New Task Assigned", "Company admin forwarded a case: " + c.getTitle());
        
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        complaintService.deleteComplaint(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long adminId = body.get("adminId");
        Complaint c = complaintRepo.findById(id).orElseThrow();
        
        // Logic fixed: Keep PENDING (default or current)
        c.setAssignedAdminId(adminId);
        if (c.getCreatedAt() == null) c.setCreatedAt(LocalDateTime.now());
        if (c.getDueDate() == null) c.setDueDate(LocalDateTime.now().plusDays(7));
        Complaint saved = complaintRepo.save(c);
        
        // Notify Admin
        User admin = userRepo.findById(adminId).orElseThrow();
        notifService.createNotification(adminId, saved.getId(), "ASSIGNMENT", "New Assignment", "You have been assigned to: " + c.getTitle());
        
        return ResponseEntity.ok(Map.of("success", true, "adminName", admin.getFullName(), "id", saved.getId(), "status", saved.getStatus()));
    }

    @PostMapping("/{id}/reassign")
    public ResponseEntity<?> reassign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long adminId = body.get("adminId");
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setAssignedAdminId(adminId);
        if (c.getCreatedAt() == null) c.setCreatedAt(LocalDateTime.now());
        if (c.getDueDate() == null) c.setDueDate(LocalDateTime.now().plusDays(7));
        Complaint saved = complaintRepo.save(c);

        // Notify Admin
        User admin = userRepo.findById(adminId).orElseThrow();
        notifService.createNotification(adminId, saved.getId(), "REASSIGNMENT", "Reassignment Request", "You have been reassigned to: " + c.getTitle());

        return ResponseEntity.ok(Map.of("success", true, "adminName", admin.getFullName(), "id", saved.getId()));
    }

    @GetMapping("/unread-counts")
    public ResponseEntity<?> getUnreadCounts(@RequestParam("userId") Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        Map<String, Long> counts = new HashMap<>();
        
        // 1. Private messages count (Announcements/Admin DM)
        counts.put("private", chatRepo.countUnreadPrivateForUser(userId));
        
        // 2. Complaint specific counts
        List<Complaint> myComplaints;
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            myComplaints = complaintRepo.findAll();
        } else if (user.getRole() == User.Role.ADMIN) {
            myComplaints = complaintRepo.findByAssignedAdminId(userId);
        } else if (user.getRole() == User.Role.CUSTOMER || user.getRole() == User.Role.EMPLOYEE) {
            myComplaints = complaintRepo.findByComplainantId(userId);
        } else {
            myComplaints = new ArrayList<>();
        }

        for (Complaint c : myComplaints) {
            long unread = chatRepo.countUnreadByComplaintAndNotSender(c.getId(), userId);
            if (unread > 0) counts.put("cmp-" + c.getId(), unread);
        }

        // Total sum for the main badge
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        counts.put("total", total);

        return ResponseEntity.ok(counts);
    }

    @GetMapping("/{id}/chat")
    public ResponseEntity<?> getChat(@PathVariable Long id, @RequestParam("userId") Long userId, @RequestParam(required = false) Long recipientId) {
        User user = userRepo.findById(userId).orElseThrow();
        boolean authorized = false;
        Complaint c = null;

        if (id == 0) {
            // Global Admin Room or Private Admin Chat
            if (user.getRole() == User.Role.SUPER_ADMIN || user.getRole() == User.Role.ADMIN) {
                authorized = true;
                if (recipientId != null) {
                    return ResponseEntity.ok(chatRepo.findPrivateMessages(userId, recipientId));
                }
            }
        } else {
            c = complaintRepo.findById(id).orElseThrow();
            if (user.getRole() == User.Role.SUPER_ADMIN) authorized = true;
            else if (user.getRole() == User.Role.ADMIN && userId.equals(c.getAssignedAdminId())) authorized = true;
            else if (user.getRole() == User.Role.COMPANY_ADMIN && user.getCompanyId().equals(c.getCompanyId())) authorized = true;
            else if (user.getRole() == User.Role.DEPT_USER && userId.equals(c.getDepartmentUserId())) authorized = true;
            else if (userId.equals(c.getComplainantId())) authorized = true;
        }

        if (!authorized) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied to this chat."));
        }
        
        return ResponseEntity.ok(chatRepo.findByComplaintIdOrderByCreatedAtAsc(id));
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<?> postChat(@PathVariable Long id, @RequestParam("userId") Long userId, @RequestParam(required = false) Long recipientId, @RequestBody ChatMessage msg) {
        User user = userRepo.findById(userId).orElseThrow();
        boolean authorized = false;
        Complaint c = null;

        if (id == 0) {
            if (user.getRole() == User.Role.SUPER_ADMIN || user.getRole() == User.Role.ADMIN) authorized = true;
        } else {
            c = complaintRepo.findById(id).orElseThrow();
            if (user.getRole() == User.Role.SUPER_ADMIN) authorized = true;
            else if (user.getRole() == User.Role.ADMIN && userId.equals(c.getAssignedAdminId())) authorized = true;
            else if (user.getRole() == User.Role.COMPANY_ADMIN && user.getCompanyId().equals(c.getCompanyId())) authorized = true;
            else if (user.getRole() == User.Role.DEPT_USER && userId.equals(c.getDepartmentUserId())) authorized = true;
            else if (userId.equals(c.getComplainantId())) authorized = true;
        }

        if (!authorized) return ResponseEntity.status(403).body(Map.of("error", "Denied"));

        msg.setComplaintId(id);
        msg.setRecipientId(recipientId);
        msg.setSenderId(userId);
        msg.setSenderName(user.getFullName() != null ? user.getFullName() : user.getUsername());
        msg.setSenderRole(user.getRole().name());
        
        if (msg.getChannel() == null || msg.getChannel().isBlank()) {
            if (user.getRole() == User.Role.DEPT_USER) msg.setChannel("department");
            else if (user.getRole() == User.Role.COMPANY_ADMIN) msg.setChannel("company");
            else msg.setChannel("employee");
        }
        if (msg.getCreatedAt() == null) msg.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(chatRepo.save(msg));
    }

    @PostMapping("/{id}/chat/read")
    public ResponseEntity<?> markChatRead(@PathVariable Long id, @RequestParam("userId") Long userId, @RequestParam("channel") String channel) {
        List<ChatMessage> messages = chatRepo.findByComplaintIdOrderByCreatedAtAsc(id);
        boolean changed = false;
        for (ChatMessage m : messages) {
            if (m.getChannel().equalsIgnoreCase(channel) && !m.getSenderId().equals(userId) && !m.isRead()) {
                m.setRead(true);
                changed = true;
            }
        }
        if (changed) chatRepo.saveAll(messages);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<?> getEvidence(@PathVariable Long id, @RequestParam("userId") Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        Complaint c = complaintRepo.findById(id).orElseThrow();

        if (user.getRole() == User.Role.ADMIN && (c.getAssignedAdminId() == null || !c.getAssignedAdminId().equals(userId))) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied to evidence."));
        }
        return ResponseEntity.ok(evidenceRepo.findByCaseId(String.valueOf(id)));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    public ResponseEntity<?> downloadEvidence(@PathVariable Long id, @PathVariable Long evidenceId, @RequestParam("userId") Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        Complaint c = complaintRepo.findById(id).orElseThrow();

        if (user.getRole() == User.Role.ADMIN && (c.getAssignedAdminId() == null || !c.getAssignedAdminId().equals(userId))) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied to download."));
        }
        
        Evidence e = evidenceRepo.findById(evidenceId).orElseThrow();
        // In a real app, send actual file stream. For now, returning URL or placeholder.
        return ResponseEntity.ok(Map.of("url", e.getFilePath(), "fileName", e.getFileName()));
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    public ResponseEntity<?> deleteEvidence(@PathVariable Long id, @PathVariable Long evidenceId, @RequestParam("userId") Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        Complaint c = complaintRepo.findById(id).orElseThrow();

        if (user.getRole() != User.Role.SUPER_ADMIN && !c.getComplainantId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied."));
        }

        if (c.getStatus() != Complaint.Status.PENDING) {
            return ResponseEntity.status(400).body(Map.of("error", "Evidence can only be deleted while complaint is PENDING."));
        }

        return evidenceRepo.findById(evidenceId)
                .map(e -> {
                    try {
                        String pathStr = e.getFilePath();
                        if (pathStr.startsWith("/")) pathStr = pathStr.substring(1);
                        Path path = Paths.get("src/main/resources/static").resolve(pathStr);
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        System.err.println("Failed to delete physical file: " + ex.getMessage());
                    }
                    evidenceRepo.delete(e);
                    return ResponseEntity.ok(Map.of("success", true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/debug-stats")
    public ResponseEntity<?> getDebugStats() {
        List<Complaint> all = complaintRepo.findAll();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", all.size());
        stats.put("customerCount", all.stream().filter(c -> "CUSTOMER".equalsIgnoreCase(c.getComplainantType())).count());
        stats.put("employeeCount", all.stream().filter(c -> "EMPLOYEE".equalsIgnoreCase(c.getComplainantType())).count());
        stats.put("sample", all.stream().limit(2).toList());
        return ResponseEntity.ok(stats);
    }
}
