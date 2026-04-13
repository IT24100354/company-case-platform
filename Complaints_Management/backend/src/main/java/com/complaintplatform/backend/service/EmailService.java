package com.complaintplatform.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text notification emails for admin account approval and rejection.
 *
 * SETUP REQUIRED before real emails will be delivered:
 * 1. Set spring.mail.username = your Gmail address in application.properties
 * 2. Set spring.mail.password = your Gmail App Password (not normal Gmail password)
 *    How to get App Password: Gmail → Manage Google Account → Security →
 *    2-Step Verification → App passwords → Generate one for "Mail"
 * 3. Set cms.email.enabled=true in application.properties
 *
 * While cms.email.enabled=false (default during development), the full email
 * content is printed to the Spring Boot console so you can verify the flow.
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * The Gmail address configured in spring.mail.username.
     * Used as the From address on outgoing emails.
     * Must match the authenticated SMTP account.
     */
    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Controls whether emails are actually sent via SMTP.
     * false (default) = print to console (safe for development)
     * true             = send real emails via SMTP (requires valid credentials)
     */
    @Value("${cms.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${spring.mail.password:}")
    private String mailSenderPassword;

    /**
     * Sends an OTP (One-Time Password) verification code to the user's email during registration.
     *
     * @param toEmail recipient's email
     * @param otp     6-digit verification code
     */
    public void sendOtpEmail(String toEmail, String otp) {
        String subject = "Password Reset OTP";
        String body = "Your OTP is: " + otp + "\n"
                + "This OTP is valid for 5 minutes.";

        sendEmail(toEmail, subject, body);
    }

    /**
     * Sends an approval notification to the registered admin's email address.
     *
     * @param toEmail  the email address the admin entered during registration
     * @param name     the admin's full name
     * @param username the admin's login username
     */
    public void sendApprovalEmail(String toEmail, String name, String username) {
        String subject = "Your Admin Account Has Been Approved – CMS";
        String body = "Dear " + name + ",\n\n"
                + "Congratulations! Your admin registration request for the Complaint Management System "
                + "has been approved by the Super Administrator.\n\n"
                + "You can now log in using your credentials:\n"
                + "  Username: " + username + "\n"
                + "  Password: the password you set during registration\n\n"
                + "Login here: http://localhost:8080/login.html\n\n"
                + "Welcome to the team!\n\n"
                + "— Complaint Management System";

        sendEmail(toEmail, subject, body);
    }

    /**
     * Sends a rejection notification to the registered admin's email address.
     *
     * @param toEmail  the email address the admin entered during registration
     * @param name     the admin's full name
     * @param reason   the rejection reason provided by Super Admin
     */
    public void sendRejectionEmail(String toEmail, String name, String reason) {
        String subject = "Your Admin Account Request Was Rejected – CMS";
        String body = "Dear " + name + ",\n\n"
                + "We regret to inform you that your admin registration request for the "
                + "Complaint Management System has been reviewed and rejected.\n\n"
                + "Reason: " + reason + "\n\n"
                + "If you believe this is an error, please contact the Super Administrator.\n\n"
                + "— Complaint Management System";

        sendEmail(toEmail, subject, body);
    }

    /**
     * Internal helper — sends via SMTP or logs to console based on cms.email.enabled.
     */
    private void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            System.out.println("\n=== [EMAIL SIMULATION — cms.email.enabled=false] ===");
            System.out.println("To:      " + to);
            System.out.println("From:    " + fromAddress);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("====================================================\n");
            return;
        }

        // Check for placeholder credentials that will cause SMTP errors
        if ("YOUR_GMAIL_ADDRESS@gmail.com".equals(fromAddress) || "YOUR_16_CHAR_APP_PASSWORD".equals(mailSenderPassword) || fromAddress == null || fromAddress.isBlank()) {
            String errorMsg = "SMTP sending is ENABLED but Gmail username or App Password is still the default placeholder in application.properties.";
            System.err.println("[EMAIL ERROR] " + errorMsg);
            throw new RuntimeException(errorMsg);
        }

        try {
            System.out.println("[EMAIL] Attempting to send message to: " + to + " via " + fromAddress);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
            
            System.out.println("[EMAIL SUCCESS] Sent message to: " + to);
        } catch (Exception e) {
            System.err.println("[EMAIL FAILURE] Could not send to " + to + ". Reason: " + e.getMessage());
            // Rethrow so the Auth controller can catch it and show error in UI
            throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
        }
    }
}
