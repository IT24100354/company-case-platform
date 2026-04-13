package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.Resolution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResolutionRepository extends JpaRepository<Resolution, Long> {
    List<Resolution> findByComplaintIdOrderBySubmittedAtDesc(Long complaintId);
    List<Resolution> findAllByOrderBySubmittedAtDesc();
}
