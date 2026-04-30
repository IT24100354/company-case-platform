package com.complaintplatform.backend.repository;

import com.complaintplatform.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRole(User.Role role);
    List<User> findByRoleAndCompanyId(User.Role role, Long companyId);
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE LOWER(TRIM(u.registrationNumber)) = LOWER(TRIM(:registrationNumber))")
    Optional<User> findByRegistrationNumber(String registrationNumber);
}
