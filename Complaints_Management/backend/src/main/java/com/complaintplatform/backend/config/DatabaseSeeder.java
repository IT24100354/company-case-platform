package com.complaintplatform.backend.config;

import com.complaintplatform.backend.model.*;
import com.complaintplatform.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Configuration
public class DatabaseSeeder {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepo, ComplaintRepository complaintRepo, 
                                   NotificationRepository notifRepo, ChatMessageRepository chatRepo,
                                   InternalNoteRepository noteRepo, CompanyRepository companyRepo,
                                   DepartmentRepository departmentRepo, EvidenceRepository evidenceRepo,
                                   CompanyEmployeeRepository employeeRepo,
                                   JdbcTemplate jdbcTemplate, PasswordEncoder encoder) {
        return args -> {
            // Check for specific super admin to determine if we need to seed
            if (userRepo.findByUsername("braveena").isPresent()) {
                System.out.println("SEEDER: Super admin 'braveena' detected. Skipping initialization to protect persistent data.");
                
                // Ensure verification table is seeded if empty (non-destructive)
                if (employeeRepo.count() == 0) {
                    seedVerificationData(employeeRepo);
                }
                return;
            }

            System.out.println("SEEDER: Starting fresh initialization (No main admin found)...");
            
            // Note: We avoid truncate/deleteAll as these can cause ID shifts 
            // and affect data persistence after restarts in some environments.
            // If the DB is empty (checked above), we simply proceed with seeding.

            // 0. Seed Companies and Departments
            Company slt_comp = null, le_comp = null, qs_comp = null, abc_comp = null, cch_comp = null;
            if (companyRepo.count() == 0) {
                slt_comp = new Company(); slt_comp.setName("SLT Digital"); slt_comp.setDescription("Telecommunications");
                slt_comp = companyRepo.save(slt_comp);
                le_comp = new Company(); le_comp.setName("Lanka Electronics"); le_comp.setDescription("Consumer electronics");
                le_comp = companyRepo.save(le_comp);
                qs_comp = new Company(); qs_comp.setName("QuickShop.lk"); qs_comp.setDescription("E-commerce");
                qs_comp = companyRepo.save(qs_comp);
                abc_comp = new Company(); abc_comp.setName("ABC Garments"); abc_comp.setDescription("Clothing & Textiles");
                abc_comp = companyRepo.save(abc_comp);
                cch_comp = new Company(); cch_comp.setName("CityCare Hospitals"); cch_comp.setDescription("Healthcare");
                cch_comp = companyRepo.save(cch_comp);

                Company finalAbc = abc_comp, finalLe = le_comp, finalSlt = slt_comp, finalQs = qs_comp, finalCch = cch_comp;
                String[][] depts = {
                    {"HR", String.valueOf(finalAbc.getId())}, {"Finance", String.valueOf(finalAbc.getId())}, {"Operations", String.valueOf(finalAbc.getId())},
                    {"Customer Support", String.valueOf(finalLe.getId())}, {"Engineering", String.valueOf(finalLe.getId())}, {"Service Center", String.valueOf(finalLe.getId())},
                    {"Technical Support", String.valueOf(finalSlt.getId())}, {"Billing Dept", String.valueOf(finalSlt.getId())}, {"Network Dept", String.valueOf(finalSlt.getId())},
                    {"Logistic", String.valueOf(finalQs.getId())}, {"Returns", String.valueOf(finalQs.getId())},
                    {"Nursing", String.valueOf(finalCch.getId())}, {"Pharmacy", String.valueOf(finalCch.getId())}, {"Radiology", String.valueOf(finalCch.getId())}
                };
                for (String[] d : depts) {
                    Department dept = new Department();
                    dept.setName(d[0]);
                    dept.setCompanyId(Long.parseLong(d[1]));
                    departmentRepo.save(dept);
                }
            } else {
                List<Company> existing = companyRepo.findAll();
                slt_comp = existing.stream().filter(c -> "SLT Digital".equals(c.getName())).findFirst().orElse(null);
                le_comp = existing.stream().filter(c -> "Lanka Electronics".equals(c.getName())).findFirst().orElse(null);
                qs_comp = existing.stream().filter(c -> "QuickShop.lk".equals(c.getName())).findFirst().orElse(null);
                abc_comp = existing.stream().filter(c -> "ABC Garments".equals(c.getName())).findFirst().orElse(null);
                cch_comp = existing.stream().filter(c -> "CityCare Hospitals".equals(c.getName())).findFirst().orElse(null);
            }

            seedVerificationData(employeeRepo);
            
            // Skip the truncate logic entirely to preserve any stray manual data
            // and only insert if missing.

            // 1. Create Super Admin
            User sa = new User();
            sa.setUsername("braveena");
            sa.setFullName("Braveena");
            sa.setRole(User.Role.SUPER_ADMIN);
            sa.setEmail("sbbraveena@gmail.com");
            sa.setPassword(encoder.encode("Braveena@123"));
            sa.setEnabled(true);
            User savedSa = userRepo.save(sa);

            // 2. Create 5 Admins Exactly as Requested
            String[][] adminData = {
                {"Nimal Perera", "admin_nimal", "nimal.perera@gmail.com"},
                {"Kasuni Silva", "admin_kasuni", "kasuni.silva@gmail.com"},
                {"Tharshan Rajan", "admin_tharshan", "tharshan.rajan@gmail.com"},
                {"Malini Sivapalan", "admin_malini", "malini.sivapalan@gmail.com"},
                {"Suresh Kumaran", "admin_suresh", "suresh.kumaran@gmail.com"}
            };

            List<User> adminList = new ArrayList<>();
            for (String[] data : adminData) {
                User a = new User();
                a.setUsername(data[1]);
                a.setFullName(data[0]);
                a.setEmail(data[2]);
                a.setRole(User.Role.ADMIN);
                a.setPassword(encoder.encode("Admin@123"));
                a.setEnabled(true);
                adminList.add(userRepo.save(a));
            }

            // 3. Create Complainants
            User emp = new User();
            emp.setUsername("emp_sahan");
            emp.setFullName("Sahan");
            emp.setEmail("sahan.employee@gmail.com");
            emp.setPassword(encoder.encode("Employee@123"));
            emp.setRole(User.Role.EMPLOYEE);
            if (abc_comp != null) {
                emp.setCompanyId(abc_comp.getId());
                emp.setCompanyName(abc_comp.getName());
            } else {
                emp.setCompanyId(101L); // Fallback if ABC Garments not found
                emp.setCompanyName("ABC Garments");
            }
            emp.setNic("123456789V");
            emp.setEmployeeId("EMP-ABC-001");
            emp.setEnabled(true);
            emp = userRepo.save(emp);

            User cust = new User();
            cust.setUsername("cust_kavindi");
            cust.setFullName("Kavindi");
            cust.setEmail("kavindi.customer@gmail.com");
            cust.setPassword(encoder.encode("Customer@123"));
            cust.setRole(User.Role.CUSTOMER);
            cust.setEnabled(true);
            cust = userRepo.save(cust);

            // 4. Seed 10 Complaints
            List<Complaint> seededComplaints = new ArrayList<>();
            
            // 1. Salary Delay (Employee, Pending)
            seededComplaints.add(createSeed(complaintRepo, "Salary Delay", "The salary for March 2026 has been delayed without any formal notification.", 
                "Finance", "EMPLOYEE", emp, abc_comp != null ? abc_comp.getId() : 101L, "ABC Garments", null, Complaint.Status.PENDING, "HIGH", null));

            // 2. Workplace Harassment (Employee, Approved)
            seededComplaints.add(createSeed(complaintRepo, "Workplace Harassment", "Verbal abuse from department head during meetings.", 
                "HR", "EMPLOYEE", emp, abc_comp != null ? abc_comp.getId() : 101L, "ABC Garments", adminList.get(0), Complaint.Status.APPROVED, "HIGH", "HR Department"));

            // 3. Damaged Product Received (Customer, Viewed)
            seededComplaints.add(createSeed(complaintRepo, "Damaged Product Received", "The laptop I ordered arrived with a cracked screen.", 
                "Logistic", "CUSTOMER", cust, le_comp != null ? le_comp.getId() : 202L, "Lanka Electronics", adminList.get(1), Complaint.Status.VIEWED, "MEDIUM", "Customer Support"));

            // 4. Technical Service Issue (Customer, In Progress)
            seededComplaints.add(createSeed(complaintRepo, "Technical Service Issue", "Internet connection drops every 10 minutes.", 
                "Technical", "CUSTOMER", cust, slt_comp != null ? slt_comp.getId() : 203L, "SLT Digital", adminList.get(2), Complaint.Status.IN_PROGRESS, "HIGH", "Engineering"));

            // 5. Overtime Payment Missing (Employee, Rejected)
            seededComplaints.add(createSeed(complaintRepo, "Overtime Payment Missing", "Logged 20 hours of overtime in February, but not reflected.", 
                "Payroll", "EMPLOYEE", emp, abc_comp != null ? abc_comp.getId() : 101L, "ABC Garments", adminList.get(3), Complaint.Status.REJECTED, "MEDIUM", "Finance"));

            // 6. Leave Request Unfairly Denied (Employee, Received Resolution)
            seededComplaints.add(createSeed(complaintRepo, "Leave Request Unfairly Denied", "Requested marriage leave 3 months in advance, rejected.", 
                "HR", "EMPLOYEE", emp, abc_comp != null ? abc_comp.getId() : 101L, "ABC Garments", adminList.get(4), Complaint.Status.RECEIVED_RESOLUTION, "MEDIUM", "HR Department"));

            // 7. Refund Not Processed (Customer, Pending)
            seededComplaints.add(createSeed(complaintRepo, "Refund Not Processed", "Requested refund for returned item 14 days ago.", 
                "Finance", "CUSTOMER", cust, qs_comp != null ? qs_comp.getId() : 204L, "QuickShop.lk", null, Complaint.Status.PENDING, "MEDIUM", null));

            // 8. Unsafe Working Conditions (Employee, Resolution Rejected)
            seededComplaints.add(createSeed(complaintRepo, "Unsafe Working Conditions", "Exposed wiring in the production line.", 
                "Safety", "EMPLOYEE", emp, abc_comp != null ? abc_comp.getId() : 101L, "ABC Garments", adminList.get(0), Complaint.Status.RESOLUTION_REJECTED, "HIGH", "Operations"));

            // 9. Poor Customer Service (Customer, Approved)
            seededComplaints.add(createSeed(complaintRepo, "Poor Customer Service", "Agent was extremely rude.", 
                "General", "CUSTOMER", cust, le_comp != null ? le_comp.getId() : 202L, "Lanka Electronics", adminList.get(1), Complaint.Status.APPROVED, "LOW", "Customer Support"));

            // 10. Wrong Invoice Generated (Customer, Resolved)
            seededComplaints.add(createSeed(complaintRepo, "Wrong Invoice Generated", "Invoiced for 5 items instead of 2.", 
                "Billing", "CUSTOMER", cust, slt_comp != null ? slt_comp.getId() : 203L, "SLT Digital", adminList.get(2), Complaint.Status.RESOLVED, "MEDIUM", "Billing Dept"));

            // 5. Seed Notifications
            addNotif(notifRepo, savedSa.getId(), "NEW_COMPLAINT", "New Complaint", "A new salary delay complaint has been filed.");
            addNotif(notifRepo, savedSa.getId(), "ASSIGNMENT", "Admin Assigned", "Nimal Perera has been assigned to a workplace harassment case.");

            // 6. Seed Internal Notes for Admins
            InternalNote n1 = new InternalNote();
            n1.setComplaintId(seededComplaints.get(1).getId());
            n1.setTitle("Urgent HR Case");
            n1.setMessage("Please handle this harassment case with strict confidentiality.");
            n1.setVisibilityType("SINGLE_ADMIN");
            n1.setAssignedAdminId("admin_nimal");
            n1.setCreatedBy("braveena");
            noteRepo.save(n1);

            InternalNote n2 = new InternalNote();
            n2.setComplaintId(seededComplaints.get(3).getId());
            n2.setTitle("Technical Review Needed");
            n2.setMessage("Check the log files for recurring disconnects.");
            n2.setVisibilityType("SINGLE_ADMIN");
            n2.setAssignedAdminId("admin_tharshan");
            n2.setCreatedBy("braveena");
            noteRepo.save(n2);

            System.out.println("Seeding complete. Super Admin: braveena / Braveena@123");
        };
    }

    private void seedVerificationData(CompanyEmployeeRepository repo) {
        String[][] raw = {
            {"Sahan Perera", "123456789V", "EMP-ABC-001", "ABC Garments"},
            {"Nalin Silva", "987654321V", "EMP-SLT-055", "SLT Digital"},
            {"Priyantha Raj", "456789123V", "EMP-LE-102", "Lanka Electronics"},
            {"Amara Fernando", "789123456V", "EMP-QS-99", "QuickShop.lk"}
        };
        for (String[] r : raw) {
            CompanyEmployee ce = new CompanyEmployee();
            ce.setName(r[0]);
            ce.setNic(r[1]);
            ce.setCompanyEmployeeId(r[2]);
            ce.setCompanyName(r[3]);
            repo.save(ce);
        }
    }

    private Complaint createSeed(ComplaintRepository repo, String title, String desc, String cat, String type, User complainant, Long companyId, String companyName, User admin, Complaint.Status status, String priority, String dept) {
        Complaint c = new Complaint();
        c.setTitle(title);
        c.setDescription(desc);
        c.setCategory(cat);
        c.setComplainantType(type);
        c.setComplainantId(complainant.getId());
        c.setComplainantName(complainant.getFullName());
        c.setCompanyId(companyId);
        c.setCompanyName(companyName);
        c.setAssignedAdminId(admin != null ? admin.getId() : null);
        c.setDepartment(dept);
        c.setStatus(status);
        c.setPriority(priority);
        c.setCreatedAt(LocalDateTime.now().minusDays((int)(Math.random() * 5)));
        return repo.save(c);
    }

    private void addNotif(NotificationRepository repo, Long recipientId, String type, String title, String msg) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(msg);
        n.setRead(false);
        repo.save(n);
    }
}
