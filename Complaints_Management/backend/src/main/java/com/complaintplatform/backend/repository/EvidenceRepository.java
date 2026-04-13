package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    List<Evidence> findByCaseId(String caseId);
}
