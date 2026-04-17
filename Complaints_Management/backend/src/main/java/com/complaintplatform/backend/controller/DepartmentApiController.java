package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.Department;
import com.complaintplatform.backend.model.User;
import com.complaintplatform.backend.repository.DepartmentRepository;
import com.complaintplatform.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/departments")
public class DepartmentApiController {

    @Autowired private DepartmentRepository departmentRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<Department>> get(@RequestParam(required = false) Long companyId) {
        if (companyId != null) {
            return ResponseEntity.ok(departmentRepo.findByCompanyId(companyId));
        }
        return ResponseEntity.ok(departmentRepo.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String deptId = request.get("deptId");
        String description = request.get("description");
        String username = request.get("username");
        String password = request.get("password");
        Long companyId = Long.parseLong(request.get("companyId"));
        String companyName = request.get("companyName");

        if (userRepo.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }

        // 1. Create Department
        Department dept = new Department();
        dept.setName(name);
        dept.setDeptId(deptId);
        dept.setDescription(description);
        dept.setCompanyId(companyId);
        departmentRepo.save(dept);

        // 2. Create Dept User
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(name + " Portal");
        user.setRole(User.Role.DEPT_USER);
        user.setDepartment(name);
        user.setCompanyId(companyId);
        user.setCompanyName(companyName);
        user.setEnabled(true);
        userRepo.save(user);

        return ResponseEntity.ok(Map.of("success", true, "message", "Department created with portal access"));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getDeptUsers(@RequestParam Long companyId) {
        List<User> users = userRepo.findAll().stream()
                .filter(u -> u.getRole() == User.Role.DEPT_USER && companyId.equals(u.getCompanyId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }
}
