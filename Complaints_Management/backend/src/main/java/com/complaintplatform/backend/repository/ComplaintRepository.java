package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByStatus(Complaint.Status status);
    List<Complaint> findByAssignedAdminId(Long adminId);
    List<Complaint> findByCompanyId(Long companyId);
    List<Complaint> findByComplainantId(Long complainantId);
    List<Complaint> findByDepartmentUserId(Long deptUserId);
    
    // Sort versions
    List<Complaint> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE complaints", nativeQuery = true)
    void truncateTable();

    @Modifying
    @Transactional
    @Query(value = "SET FOREIGN_KEY_CHECKS = 0; TRUNCATE TABLE users; SET FOREIGN_KEY_CHECKS = 1;", nativeQuery = true)
    void truncateUsers();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE complaints MODIFY COLUMN status VARCHAR(255)", nativeQuery = true)
    void fixStatusColumn();
}