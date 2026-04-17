package com.complaintplatform.backend.controller.admin;

import com.complaintplatform.backend.model.User;
import com.complaintplatform.backend.repository.UserRepository;
import com.complaintplatform.backend.repository.CompanyRepository;
import com.complaintplatform.backend.model.Company;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/requests")
public class AdminRequestController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRequests(@RequestParam(value = "status", required = false, defaultValue = "PENDING") String status) {
        boolean enabled = status.equalsIgnoreCase("APPROVED");
        
        List<User> users = userRepository.findAll().stream()
                .filter(u -> (u.getRole() == User.Role.ADMIN || u.getRole() == User.Role.COMPANY_ADMIN))
                .filter(u -> u.isEnabled() == enabled)
                .collect(Collectors.toList());

        List<Map<String, Object>> resp = users.stream().map(u -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getFullName());
            map.put("username", u.getUsername());
            map.put("email", u.getEmail() != null ? u.getEmail() : "");
            map.put("role", u.getRole().name());
            map.put("companyName", u.getCompanyName() != null ? u.getCompanyName() : "");
            map.put("nic", u.getNic() != null ? u.getNic() : "");
            map.put("registrationNumber", u.getRegistrationNumber() != null ? u.getRegistrationNumber() : "");
            map.put("approvalStatus", u.isEnabled() ? "APPROVED" : "PENDING");
            map.put("registeredAt", u.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return userRepository.findById(id).map(user -> {
            user.setEnabled(true);
            
            // If it's a company admin, maybe create the company if it doesn't exist
            if (user.getRole() == User.Role.COMPANY_ADMIN && user.getCompanyName() != null) {
                boolean exists = companyRepository.findAll().stream()
                        .anyMatch(c -> c.getName().equalsIgnoreCase(user.getCompanyName()));
                if (!exists) {
                    Company company = new Company();
                    company.setName(user.getCompanyName());
                    company.setDescription("Registered by " + user.getFullName());
                    Company savedCompany = companyRepository.save(company);
                    user.setCompanyId(savedCompany.getId());
                }
            }
            
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User approved successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return userRepository.findById(id).map(user -> {
            // In a real app, maybe delete or mark as rejected. For now, let's delete to clear the queue
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User request rejected and removed"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
