package com.complaintplatform.backend.controller;

import com.complaintplatform.backend.model.Company;
import com.complaintplatform.backend.model.Department;
import com.complaintplatform.backend.repository.CompanyRepository;
import com.complaintplatform.backend.repository.DepartmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class GeneralApiController {

    private final CompanyRepository companyRepo;
    private final DepartmentRepository departmentRepo;

    public GeneralApiController(CompanyRepository companyRepo, DepartmentRepository departmentRepo) {
        this.companyRepo = companyRepo;
        this.departmentRepo = departmentRepo;
    }

    @GetMapping("/companies")
    public ResponseEntity<List<Company>> getCompanies() {
        return ResponseEntity.ok(companyRepo.findAll());
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getDepartments(@RequestParam(value = "companyId", required = false) Long companyId) {
        if (companyId != null) {
            return ResponseEntity.ok(departmentRepo.findByCompanyId(companyId));
        }
        return ResponseEntity.ok(departmentRepo.findAll());
    }
}
