package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.User;
import com.complaintplatform.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.complaintplatform.backend.service.EmailService;
import java.time.LocalDateTime;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import com.complaintplatform.backend.repository.CompanyEmployeeRepository;
import com.complaintplatform.backend.repository.CompanyRepository;
import com.complaintplatform.backend.model.CompanyEmployee;

@RestController
@RequestMapping("/api/auth")
public class UnifiedAuthController {
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CompanyEmployeeRepository companyEmployeeRepository;
    @Autowired private CompanyRepository companyRepository;

    @GetMapping("/debug/employees")
    public ResponseEntity<?> debugEmployees() {
        return ResponseEntity.ok(companyEmployeeRepository.findAll());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String roleStr = request.get("role");
        String username = request.get("username");
        String password = request.get("password");
        String fullName = request.get("fullName");
        String email = request.get("email");
        String nic = request.get("nic");
        String employeeId = request.get("employeeId");
        String companyName = request.get("companyName");
        String registrationNumber = request.get("registrationNumber") != null ? request.get("registrationNumber").trim() : null;
        String companyPolicies = request.get("companyPolicies");
        
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(400).body(Map.of("message", "Username already exists."));
        }

        if (registrationNumber != null && !registrationNumber.isBlank()) {
            if (userRepository.findByRegistrationNumber(registrationNumber).isPresent()) {
                return ResponseEntity.status(400).body(Map.of("message", "Company registration number already exists."));
            }
        }

        // NIC Validation: 10 characters (9 digits + V/v) or 10 digits
        if (nic != null && !nic.isBlank()) {
            if (!nic.matches("^([0-9]{9}[Vv]|[0-9]{10})$")) {
                return ResponseEntity.status(400).body(Map.of("message", "Invalid NIC format. Must be 9 digits followed by 'V' or 10 digits."));
            }
        }

        User.Role role = User.Role.valueOf(roleStr.toUpperCase());
        
        if (role == User.Role.EMPLOYEE) {
            // Strict Validation
            Optional<CompanyEmployee> verified = companyEmployeeRepository.findByNicAndCompanyEmployeeId(nic, employeeId);
            if (verified.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("message", "Wrong NIC or Employee ID"));
            }
            
            // Link company if matched by name
            CompanyEmployee ce = verified.get();
            if (companyName != null && !companyName.equalsIgnoreCase(ce.getCompanyName())) {
                 return ResponseEntity.status(400).body(Map.of("message", "Company mismatch for this Employee ID."));
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setNic(nic);
        user.setEmployeeId(employeeId);
        user.setCompanyName(companyName);
        user.setRegistrationNumber(registrationNumber);
        user.setCompanyPolicies(companyPolicies);
        
        // Find company ID if possible (only for roles that link to existing companies, like EMPLOYEE)
        if (companyName != null && role == User.Role.EMPLOYEE) {
            companyRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(companyName))
                .findFirst()
                .ifPresent(c -> user.setCompanyId(c.getId()));
        }

        // AUTO-APPROVE Employees and Customers
        if (role == User.Role.EMPLOYEE || role == User.Role.CUSTOMER) {
            user.setEnabled(true);
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Registration successful", "success", true));
    }

    @GetMapping("/identify")
    public ResponseEntity<?> identify(@RequestParam String username) {
        return userRepository.findByUsername(username)
                .map(u -> ResponseEntity.ok(Map.of("role", u.getRole().name())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            User user = userOpt.get();
            
            if (!user.isEnabled()) {
                return ResponseEntity.status(403).body(Map.of("message", "Your account is pending approval by the Super Admin."));
            }

            return ResponseEntity.ok(buildSessionMap(user));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
    }

    @Value("${google.client.id:275638293160-fbnt7thi76uqtc50cg3bqluau2dpmfpf.apps.googleusercontent.com}")
    private String googleClientId;

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        String idTokenString = request.get("credential");
        if (idTokenString == null || idTokenString.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ID Token is missing."));
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                
                // Requirement 2: Check if email exists in DB
                Optional<User> userOpt = userRepository.findAll().stream()
                    .filter(u -> email != null && email.equalsIgnoreCase(u.getEmail()))
                    .findFirst();

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!user.isEnabled()) {
                        return ResponseEntity.status(403).body(Map.of("message", "Your account is pending approval."));
                    }
                    // Requirement 3 & 4: Log in with DB role and build session
                    return ResponseEntity.ok(buildSessionMap(user));
                } else {
                    // Requirement 5: Reject if not found
                    return ResponseEntity.status(401).body(Map.of("message", "This Google account ("+email+") is not registered in the system."));
                }
            } else {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid Google ID Token."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Authentication error: " + e.getMessage()));
        }
    }

    private Map<String, Object> buildSessionMap(User user) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", user.getId());
        resp.put("username", user.getUsername());
        resp.put("fullName", user.getFullName());
        resp.put("role", user.getRole().name());
        resp.put("companyId", user.getCompanyId());
        resp.put("companyName", user.getCompanyName());
        resp.put("department", user.getDepartment());
        resp.put("profileImageUrl", user.getProfileImageUrl());
        resp.put("nic", user.getNic());
        resp.put("registrationNumber", user.getRegistrationNumber());
        resp.put("companyPolicies", user.getCompanyPolicies());
        resp.put("email", user.getEmail());
        return resp;
    }

    @Autowired private EmailService emailService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Username not found."));
        }

        User user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("message", "No email address found for this user."));
        }

        // Generate 4-digit OTP
        String otp = String.format("%04d", new Random().nextInt(10000));
        
        System.out.println("[DEBUG] Forgot Password initiated for: " + username);
        System.out.println("[DEBUG] Matched Email: " + user.getEmail());
        System.out.println("[DEBUG] Generated OTP: " + otp);

        try {
            System.out.println("[DEBUG] Attempting to call emailService.sendOtpEmail()...");
            // Note: This will now throw if email is not enabled or fails
            emailService.sendOtpEmail(user.getEmail(), otp);
            
            // SAVE ONLY AFTER SUCCESSFUL EMAIL SEND
            user.setOtp(otp);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
            userRepository.save(user);
            
            System.out.println("[DEBUG] Email sent successfully. OTP saved to DB.");
            return ResponseEntity.ok(Map.of("message", "OTP sent to your email account: " + user.getEmail(), "success", true));
        } catch (Exception e) {
            System.err.println("[DEBUG] EMAIL SENDING FAILED: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String otp = request.get("otp");

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found."));

        User user = userOpt.get();
        if (user.getOtp() == null || !user.getOtp().equals(otp)) {
            return ResponseEntity.status(400).body(Map.of("message", "Invalid OTP."));
        }
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(400).body(Map.of("message", "OTP has expired."));
        }

        return ResponseEntity.ok(Map.of("message", "OTP verified.", "success", true));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String otp = request.get("otp");
        String newPassword = request.get("password");

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found."));

        User user = userOpt.get();
        // Final verify for safety
        if (user.getOtp() == null || !user.getOtp().equals(otp) || user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(400).body(Map.of("message", "OTP verification failed."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully.", "success", true));
    }

    @PostMapping("/profile/update")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> request) {
        Long userId = request.get("id") instanceof Number ? 
                     ((Number)request.get("id")).longValue() : 
                     Long.parseLong(String.valueOf(request.get("id")));
        
        String newName = (String) request.get("fullName");
        String newEmail = (String) request.get("email");
        String newPassword = (String) request.get("password");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (newName != null && !newName.isBlank()) user.setFullName(newName);
        if (newEmail != null && !newEmail.isBlank()) user.setEmail(newEmail);
        if (newPassword != null && !newPassword.isBlank()) user.setPassword(passwordEncoder.encode(newPassword));
        
        String newCompanyPolicies = (String) request.get("companyPolicies");
        if (newCompanyPolicies != null) {
            user.setCompanyPolicies(newCompanyPolicies);
            if (user.getCompanyId() != null) {
                companyRepository.findById(user.getCompanyId()).ifPresent(company -> {
                    company.setCompanyPolicies(newCompanyPolicies);
                    companyRepository.save(company);
                });
            }
        }

        User saved = userRepository.save(user);
        Map<String, Object> respData = new HashMap<>();
        respData.put("fullName", saved.getFullName());
        respData.put("email", saved.getEmail());
        respData.put("profileImageUrl", saved.getProfileImageUrl());
        respData.put("companyPolicies", saved.getCompanyPolicies());
        respData.put("success", true);
        return ResponseEntity.ok(respData);
    }

    @Value("${cms.upload.path:uploads}")
    private String uploadDir;

    @PostMapping("/profile/image")
    public ResponseEntity<?> uploadImage(@RequestParam("id") Long userId, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");

        String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png").contains(ext)) {
            return ResponseEntity.badRequest().body("Invalid file type. Only JPG, JPEG and PNG allowed.");
        }

        String fileName = "profile_" + userId + "_" + System.currentTimeMillis() + "." + ext;
        
        // Use the configured uploadDir from properties
        Path uploadPath = Paths.get(uploadDir, "profiles");
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Store as /uploads/... for absolute web path consistency
        String url = "/uploads/profiles/" + fileName;
        user.setProfileImageUrl(url);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("url", url, "success", true));
    }
}
