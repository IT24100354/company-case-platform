package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.*;
import com.complaintplatform.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api")
public class InternalNoteApiController {

    @Autowired private InternalNoteRepository noteRepo;
    @Autowired private UserRepository userRepo;

    // Super Admin: Get all notes created by them
    @GetMapping("/super-admin/notes")
    public ResponseEntity<?> getSuperAdminNotes(@RequestParam String username) {
        return ResponseEntity.ok(noteRepo.findByCreatedBy(username));
    }

    // Super Admin: Create a note for a complaint
    @PostMapping("/super-admin/complaints/{complaintId}/notes")
    public ResponseEntity<?> createNote(@PathVariable Long complaintId, @RequestBody InternalNote note) {
        note.setComplaintId(complaintId);
        noteRepo.save(note);
        return ResponseEntity.ok(Map.of("success", true, "message", "Note created successfully"));
    }

    // Admin: Get notes assigned to them
    @GetMapping("/admin/notes/my")
    public ResponseEntity<?> getMyNotes(@RequestParam String username) {
        Optional<User> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("User not found");
        
        User user = userOpt.get();
        List<InternalNote> singleNotes = noteRepo.findByAssignedAdminIdOrVisibilityType(username, "SINGLE_ADMIN");
        // Only keep those matching the username specifically
        List<InternalNote> mySingle = singleNotes.stream().filter(n -> username.equals(n.getAssignedAdminId())).toList();

        List<InternalNote> companyNotes = new ArrayList<>();
        if (user.getCompanyId() != null) {
            companyNotes = noteRepo.findByCompanyIdAndVisibilityType(String.valueOf(user.getCompanyId()), "COMPANY_ADMINS");
        }

        Set<InternalNote> all = new HashSet<>(mySingle);
        all.addAll(companyNotes);
        
        List<InternalNote> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        
        return ResponseEntity.ok(sorted);
    }
}
