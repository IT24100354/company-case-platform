package com.complaintplatform.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "complaints")
public class Complaint {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        FORWARDED_TO_COMPANY,
        VIEWED_BY_COMPANY,
        FORWARDED_TO_DEPT,
        IN_PROGRESS,
        RECEIVED_RESOLUTION,
        RESOLUTION_SENT,
        RESOLUTION_REJECTED,
        RESOLVED,
        MORE_INFO_REQUIRED,
        // Legacy support if needed
        FORWARDED,
        VIEWED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    private String category;

    @Column(nullable = false)
    @JsonProperty("complainantType")
    private String complainantType = "EMPLOYEE";

    private Long complainantId;
    private String complainantName;
    private Long companyId;
    private String companyName;
    private Long assignedAdminId;
    private Long departmentUserId;
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonProperty("status")
    private Status status = Status.PENDING;

    @JsonProperty("priority")
    private String priority;
    
    @JsonProperty("dueDate")
    private LocalDateTime dueDate;

    @Column(nullable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime approvedAt;
    private LocalDateTime forwardedAt;
    private LocalDateTime viewedByCompanyAt;
    private LocalDateTime sentToDepartmentAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime rejectedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    private LocalDateTime dateOfIncident;
    private String witnessName;
    private boolean previouslyReported = false;
    private boolean urgency = false;
    private String orderReference;
    private String desiredSolution;
    private String otherDepartment;
    private String otherCategory;
    private String otherSolution;
    private String videoLink;
    
    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getComplainantType() { return complainantType; }
    public void setComplainantType(String complainantType) { this.complainantType = complainantType; }
    public Long getComplainantId() { return complainantId; }
    public void setComplainantId(Long complainantId) { this.complainantId = complainantId; }
    public String getComplainantName() { return complainantName; }
    public void setComplainantName(String complainantName) { this.complainantName = complainantName; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Long getAssignedAdminId() { return assignedAdminId; }
    public void setAssignedAdminId(Long assignedAdminId) { this.assignedAdminId = assignedAdminId; }
    public Long getDepartmentUserId() { return departmentUserId; }
    public void setDepartmentUserId(Long departmentUserId) { this.departmentUserId = departmentUserId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getForwardedAt() { return forwardedAt; }
    public void setForwardedAt(LocalDateTime forwardedAt) { this.forwardedAt = forwardedAt; }
    public LocalDateTime getViewedByCompanyAt() { return viewedByCompanyAt; }
    public void setViewedByCompanyAt(LocalDateTime viewedByCompanyAt) { this.viewedByCompanyAt = viewedByCompanyAt; }
    public LocalDateTime getSentToDepartmentAt() { return sentToDepartmentAt; }
    public void setSentToDepartmentAt(LocalDateTime sentToDepartmentAt) { this.sentToDepartmentAt = sentToDepartmentAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public LocalDateTime getDateOfIncident() { return dateOfIncident; }
    public void setDateOfIncident(LocalDateTime dateOfIncident) { this.dateOfIncident = dateOfIncident; }
    public String getWitnessName() { return witnessName; }
    public void setWitnessName(String witnessName) { this.witnessName = witnessName; }
    public boolean isPreviouslyReported() { return previouslyReported; }
    public void setPreviouslyReported(boolean previouslyReported) { this.previouslyReported = previouslyReported; }
    public boolean isUrgency() { return urgency; }
    public void setUrgency(boolean urgency) { this.urgency = urgency; }
    public String getOrderReference() { return orderReference; }
    public void setOrderReference(String orderReference) { this.orderReference = orderReference; }
    public String getDesiredSolution() { return desiredSolution; }
    public void setDesiredSolution(String desiredSolution) { this.desiredSolution = desiredSolution; }
    public String getOtherDepartment() { return otherDepartment; }
    public void setOtherDepartment(String otherDepartment) { this.otherDepartment = otherDepartment; }
    public String getOtherCategory() { return otherCategory; }
    public void setOtherCategory(String otherCategory) { this.otherCategory = otherCategory; }
    public String getOtherSolution() { return otherSolution; }
    public void setOtherSolution(String otherSolution) { this.otherSolution = otherSolution; }
    public String getVideoLink() { return videoLink; }
    public void setVideoLink(String videoLink) { this.videoLink = videoLink; }
    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
}