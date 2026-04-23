package com.complaintplatform.backend.service;

import com.complaintplatform.backend.model.*;
import com.complaintplatform.backend.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {
    private final ComplaintRepository complaintRepo;
    private final ResolutionRepository resolutionRepo;

    public ComplaintService(ComplaintRepository complaintRepo, ResolutionRepository resolutionRepo) {
        this.complaintRepo = complaintRepo;
        this.resolutionRepo = resolutionRepo;
    }

    public List<Complaint> getAll() {
        return complaintRepo.findAll();
    }

    public List<Complaint> getByStatus(Complaint.Status status) {
        return complaintRepo.findByStatus(status);
    }

    public List<Complaint> getForAdmin(Long adminId) {
        return complaintRepo.findByAssignedAdminId(adminId);
    }

    public List<Complaint> getForCompany(Long companyId) {
        return complaintRepo.findByCompanyId(companyId);
    }

    public List<Complaint> getForDeptUser(Long deptUserId) {
        return complaintRepo.findByDepartmentUserId(deptUserId);
    }

    public List<Complaint> getForComplainant(Long userId) {
        return complaintRepo.findByComplainantId(userId);
    }

    public Complaint submitComplaint(Complaint c) {
        c.setStatus(Complaint.Status.PENDING);
        c.setCreatedAt(LocalDateTime.now());
        return complaintRepo.save(c);
    }

    public Complaint assignToAdmin(Long id, Long adminId) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setAssignedAdminId(adminId);
        // User request: "don't change the status"
        return complaintRepo.save(c);
    }

    public Complaint approve(Long id) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setStatus(Complaint.Status.APPROVED);
        c.setApprovedAt(LocalDateTime.now());
        return complaintRepo.save(c);
    }

    public Complaint reject(Long id, String reason) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setStatus(Complaint.Status.REJECTED);
        c.setRejectedAt(LocalDateTime.now());
        c.setRejectionReason(reason);
        return complaintRepo.save(c);
    }

    @org.springframework.transaction.annotation.Transactional
    public Complaint forward(Long id, String priority) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setStatus(Complaint.Status.FORWARDED_TO_COMPANY);
        c.setForwardedAt(LocalDateTime.now());
        if (c.getApprovedAt() == null) {
            c.setApprovedAt(LocalDateTime.now());
        }
        c.setPriority(priority);
        // Calculate due date based on priority
        int days = 30; // Default
        if ("HIGH".equalsIgnoreCase(priority)) days = 10;
        else if ("MEDIUM".equalsIgnoreCase(priority)) days = 20;
        c.setDueDate(LocalDateTime.now().plusDays(days));
        System.out.println("DEBUG: Forwarding complaint " + id + " with priority " + priority + ". New Status: " + c.getStatus());
        return complaintRepo.save(c);
    }

    public Complaint markViewedByCompany(Long id) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setStatus(Complaint.Status.VIEWED_BY_COMPANY);
        c.setViewedByCompanyAt(LocalDateTime.now());
        return complaintRepo.save(c);
    }

    public Complaint sendToDepartment(Long id, Long deptUserId) {
        Complaint c = complaintRepo.findById(id).orElseThrow();
        c.setDepartmentUserId(deptUserId);
        c.setStatus(Complaint.Status.FORWARDED_TO_DEPT);
        c.setSentToDepartmentAt(LocalDateTime.now());
        return complaintRepo.save(c);
    }

    public void deleteComplaint(Long id) {
        complaintRepo.deleteById(id);
    }

    public Resolution submitResolution(Resolution r) {
        r.setSubmittedAt(LocalDateTime.now());
        Resolution saved = resolutionRepo.save(r);
        
        Complaint c = complaintRepo.findById(r.getComplaintId()).orElseThrow();
        c.setStatus(Complaint.Status.RECEIVED_RESOLUTION);
        complaintRepo.save(c);
        
        return saved;
    }

    public Resolution approveResolution(Long resId) {
        Resolution r = resolutionRepo.findById(resId).orElseThrow();
        r.setDecision(Resolution.DecisionStatus.APPROVED);
        r.setDecidedAt(LocalDateTime.now());
        resolutionRepo.save(r);
        
        Complaint c = complaintRepo.findById(r.getComplaintId()).orElseThrow();
        c.setStatus(Complaint.Status.RESOLVED);
        c.setResolvedAt(LocalDateTime.now());
        complaintRepo.save(c);
        
        return r;
    }

    public Resolution rejectResolution(Long resId, String reason) {
        Resolution r = resolutionRepo.findById(resId).orElseThrow();
        r.setDecision(Resolution.DecisionStatus.REJECTED);
        r.setRejectionReason(reason);
        r.setDecidedAt(LocalDateTime.now());
        resolutionRepo.save(r);
        
        Complaint c = complaintRepo.findById(r.getComplaintId()).orElseThrow();
        c.setStatus(Complaint.Status.RESOLUTION_REJECTED); 
        complaintRepo.save(c);
        
        return r;
    }
}
