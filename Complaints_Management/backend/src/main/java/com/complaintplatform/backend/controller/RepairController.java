package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.*;
import com.complaintplatform.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/repair")
public class RepairController {

    @Autowired private ComplaintRepository complaintRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private InternalNoteRepository noteRepo;
    @Autowired private NotificationRepository notifRepo;
    @Autowired private ChatMessageRepository chatRepo;
    @Autowired private PasswordEncoder encoder;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalComplaints", complaintRepo.count());
        stats.put("totalUsers", userRepo.count());
        stats.put("totalNotes", noteRepo.count());
        stats.put("totalNotifs", notifRepo.count());
        stats.put("totalChats", chatRepo.count());
        
        List<User> admins = userRepo.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ADMIN).toList();
        stats.put("adminCount", admins.size());
        stats.put("admins", admins.stream().map(User::getUsername).toList());
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetAll() {
        // Force thorough cleanup
        chatRepo.deleteAll();
        notifRepo.deleteAll();
        noteRepo.deleteAll();
        complaintRepo.deleteAll();
        userRepo.deleteAll();

        // Seed users
        User sa = new User();
        sa.setUsername("braveena");
        sa.setFullName("Braveena");
        sa.setRole(User.Role.SUPER_ADMIN);
        sa.setPassword(encoder.encode("Braveena@123"));
        userRepo.save(sa);

        String[] admins = {"admin_nimal", "admin_kasuni", "admin_tharshan", "admin_malini", "admin_suresh"};
        List<User> savedAdmins = new ArrayList<>();
        for (String u : admins) {
            User a = new User();
            a.setUsername(u);
            a.setFullName(u.replace("admin_", "").substring(0,1).toUpperCase() + u.replace("admin_", "").substring(1));
            a.setRole(User.Role.ADMIN);
            a.setPassword(encoder.encode("Admin@123"));
            savedAdmins.add(userRepo.save(a));
        }

        User emp = new User();
        emp.setUsername("emp_sahan");
        emp.setFullName("Sahan");
        emp.setRole(User.Role.EMPLOYEE);
        emp.setCompanyId(101L);
        emp.setCompanyName("Global Garments");
        emp.setPassword(encoder.encode("Employee@123"));
        User savedEmp = userRepo.save(emp);

        User cust = new User();
        cust.setUsername("cust_kavindi");
        cust.setFullName("Kavindi");
        cust.setRole(User.Role.CUSTOMER);
        cust.setPassword(encoder.encode("Customer@123"));
        User savedCust = userRepo.save(cust);

        // Seed some complaints
        for (int i=1; i<=5; i++) {
            Complaint c = new Complaint();
            c.setTitle("Test Complaint " + i);
            c.setDescription("Description " + i);
            c.setComplainantType(i % 2 == 0 ? "CUSTOMER" : "EMPLOYEE");
            c.setComplainantId(i % 2 == 0 ? savedCust.getId() : savedEmp.getId());
            c.setComplainantName(i % 2 == 0 ? savedCust.getFullName() : savedEmp.getFullName());
            c.setStatus(Complaint.Status.PENDING);
            c.setAssignedAdminId(savedAdmins.get(i-1).getId());
            complaintRepo.save(c);
        }

        return ResponseEntity.ok(Map.of("message", "System reset and seeded successfully", "stats", getStats().getBody()));
    }
}
