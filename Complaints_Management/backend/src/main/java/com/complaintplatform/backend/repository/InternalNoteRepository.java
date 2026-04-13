package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.InternalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InternalNoteRepository extends JpaRepository<InternalNote, Long> {
    List<InternalNote> findByCreatedBy(String username);
    List<InternalNote> findByAssignedAdminIdOrVisibilityType(String username, String visibilityType);
    List<InternalNote> findByCompanyIdAndVisibilityType(String companyId, String visibilityType);
    List<InternalNote> findByComplaintId(Long complaintId);
}
