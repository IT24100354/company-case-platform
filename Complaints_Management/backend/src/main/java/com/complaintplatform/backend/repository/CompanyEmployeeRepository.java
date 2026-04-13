package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.CompanyEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyEmployeeRepository extends JpaRepository<CompanyEmployee, Long> {
    Optional<CompanyEmployee> findByNicAndCompanyEmployeeId(String nic, String companyEmployeeId);
}
