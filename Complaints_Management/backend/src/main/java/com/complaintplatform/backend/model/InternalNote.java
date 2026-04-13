package com.complaintplatform.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "internal_notes")
public class InternalNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long complaintId;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    private String visibilityType; // SINGLE_ADMIN, COMPANY_ADMINS
    private String assignedAdminId; // String to match username or ID string
    private String companyId;
    private String createdBy;
    private LocalDateTime createdAt;

    public InternalNote() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getVisibilityType() { return visibilityType; }
    public void setVisibilityType(String visibilityType) { this.visibilityType = visibilityType; }
    public String getAssignedAdminId() { return assignedAdminId; }
    public void setAssignedAdminId(String assignedAdminId) { this.assignedAdminId = assignedAdminId; }
    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
