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
import java.util.Arrays;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.awt.Color;

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

    @GetMapping("/companies")
    public ResponseEntity<List<User>> getAllCompanies() {
        return ResponseEntity.ok(userRepository.findByRole(User.Role.COMPANY_ADMIN).stream()
                .filter(User::isEnabled)
                .collect(Collectors.toList()));
    }

    @GetMapping("/employees")
    public ResponseEntity<List<User>> getAllEmployees() {
        return ResponseEntity.ok(userRepository.findByRole(User.Role.EMPLOYEE).stream()
                .filter(User::isEnabled)
                .collect(Collectors.toList()));
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
                    company.setCompanyPolicies(user.getCompanyPolicies());
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

    @GetMapping("/{id}/pdf")
    public void exportToPDF(@PathVariable Long id, HttpServletResponse response) throws IOException {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        response.setContentType("application/pdf");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=Admin_Request_" + user.getUsername() + ".pdf";
        response.setHeader(headerKey, headerValue);

        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();
        
        // Font setup
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        titleFont.setSize(18);
        titleFont.setColor(new Color(225, 29, 72)); // CMS primary color

        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        headFont.setSize(12);

        Paragraph p = new Paragraph("Admin Registration Request Details", titleFont);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        p.setSpacingAfter(20);
        document.add(p);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[] {3.0f, 7.0f});
        table.setSpacingBefore(10);

        writeTableCell(table, "Request ID", String.valueOf(user.getId()), headFont);
        writeTableCell(table, "Full Name", user.getFullName(), headFont);
        writeTableCell(table, "Username", user.getUsername(), headFont);
        writeTableCell(table, "Email", user.getEmail(), headFont);
        writeTableCell(table, "Role", user.getRole().name(), headFont);
        writeTableCell(table, "National ID (NIC)", user.getNic(), headFont);
        
        if (user.getRole() == User.Role.COMPANY_ADMIN) {
            writeTableCell(table, "Company Name", user.getCompanyName(), headFont);
            writeTableCell(table, "Registration Number", user.getRegistrationNumber(), headFont);
        }

        writeTableCell(table, "Registration Date", user.getCreatedAt().toString(), headFont);
        writeTableCell(table, "Approval Status", user.isEnabled() ? "APPROVED" : "PENDING", headFont);

        document.add(table);

        if (user.getCompanyPolicies() != null && !user.getCompanyPolicies().isBlank()) {
            Paragraph policyTitle = new Paragraph("\nCompany Policies:", headFont);
            policyTitle.setSpacingBefore(20);
            document.add(policyTitle);
            
            Paragraph policyContent = new Paragraph(user.getCompanyPolicies());
            policyContent.setSpacingBefore(5);
            document.add(policyContent);
        }

        document.close();
    }

    private void writeTableCell(PdfPTable table, String label, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(label, font));
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setPadding(8);
        table.addCell(cell);

        cell = new PdfPCell(new Phrase(value != null ? value : "N/A"));
        cell.setPadding(8);
        table.addCell(cell);
    }

    @PostMapping("/cleanup-duplicates")
    public ResponseEntity<?> cleanupDuplicates() {
        List<User> allUsers = userRepository.findAll();
        Map<String, User> firstOccurrences = new java.util.HashMap<>();
        List<User> toDelete = new java.util.ArrayList<>();

        // Explicitly delete VK and company2 if they exist as duplicates
        List<String> targetedUsernames = Arrays.asList("VK", "company2");

        for (User u : allUsers) {
            String reg = u.getRegistrationNumber();
            if (reg != null && !reg.isBlank()) {
                String key = reg.trim().toLowerCase();
                if (firstOccurrences.containsKey(key)) {
                    toDelete.add(u);
                } else {
                    firstOccurrences.put(key, u);
                }
            }
            
            if (targetedUsernames.contains(u.getUsername())) {
                if (!toDelete.contains(u)) toDelete.add(u);
            }
        }

        userRepository.deleteAll(toDelete);
        return ResponseEntity.ok(Map.of("success", true, "message", "Deleted " + toDelete.size() + " duplicate/target records."));
    }
}
