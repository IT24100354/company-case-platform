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
            // Manual cleanup of duplicate/invalid accounts
            userRepo.findByUsername("vk").ifPresent(u -> {
                System.out.println("SEEDER: Deleting requested user 'vk'...");
                userRepo.delete(u);
            });
            userRepo.findByUsername("company2").ifPresent(u -> {
                System.out.println("SEEDER: Deleting requested user 'company2'...");
                userRepo.delete(u);
            });

            // 1. Essential Core Initialization (If super admin missing)
            if (userRepo.findByUsername("braveena").isEmpty()) {
                System.out.println("SEEDER: Fresh start. Initializing core data...");
                initializeCoreData(userRepo, companyRepo, departmentRepo, encoder);
            } else {
                syncAdminProfiles(userRepo, encoder);
            }

            // 2. Ensure Specialized Accounts (Company Admins & Dept Users) are present
            ensureSpecializedAccounts(userRepo, companyRepo, encoder);

            // 3. Ensure verification table is seeded if empty
            if (employeeRepo.count() == 0) {
                seedVerificationData(employeeRepo);
            }

            // 4. Seed 10 Complaints if repository has very few items (force refresh)
            if (complaintRepo.count() < 5) {
                System.out.println("SEEDER: Database has few/no complaints. Seeding fresh complaints...");
                seedComplaints(userRepo, complaintRepo);
            }
            
            System.out.println("SEEDER: Database check complete.");
        };
    }

    private void initializeCoreData(UserRepository userRepo, CompanyRepository companyRepo, DepartmentRepository departmentRepo, PasswordEncoder encoder) {
        // Create Super Admin
        User sa = new User();
        sa.setUsername("braveena");
        sa.setFullName("Braveena");
        sa.setRole(User.Role.SUPER_ADMIN);
        sa.setEmail("sbbraveena@gmail.com");
        sa.setPhone("+94 77 123 4567");
        sa.setDepartment("Head Office");
        sa.setPassword(encoder.encode("Braveena@123"));
        sa.setProfileImageUrl("assets/braveena_profile.png");
        sa.setEnabled(true);
        userRepo.save(sa);

        // Create 5 Admins
        String[][] adminData = {
            {"Nimal Perera", "admin_nimal", "nimal.perera@gmail.com", "+94 77 765 4321"},
            {"Kasuni Silva", "admin_kasuni", "kasuni.silva@gmail.com", "+94 77 888 9999"},
            {"Tharshan Rajan", "admin_tharshan", "tharshan.rajan@gmail.com", "+94 71 222 3333"},
            {"Malini Sivapalan", "admin_malini", "malini.sivapalan@gmail.com", "+94 75 444 5555"},
            {"Suresh Kumaran", "admin_suresh", "suresh.kumaran@gmail.com", "+94 72 666 7777"}
        };
        for (String[] data : adminData) {
            User a = new User();
            a.setUsername(data[1]); a.setFullName(data[0]); a.setEmail(data[2]);
            a.setPhone(data[3]); a.setDepartment("Admin");
            a.setRole(User.Role.ADMIN); a.setPassword(encoder.encode("Admin@123")); a.setEnabled(true);
            userRepo.save(a);
        }

        // Create initial companies if missing
        String[] companyNames = {"SLT Digital", "Lanka Electronics", "QuickShop.lk", "ABC Garments"};
        for (String name : companyNames) {
            if (companyRepo.findAll().stream().noneMatch(c -> c.getName().equalsIgnoreCase(name))) {
                Company c = new Company();
                c.setName(name);
                c.setDescription(name + " registered via seeder");
                companyRepo.save(c);
            }
        }
    }

    private void ensureSpecializedAccounts(UserRepository userRepo, CompanyRepository companyRepo, PasswordEncoder encoder) {
        List<Company> companies = companyRepo.findAll();
        Company slt = companies.stream().filter(c -> c.getName().equalsIgnoreCase("SLT Digital")).findFirst().orElse(null);
        Company le = companies.stream().filter(c -> c.getName().equalsIgnoreCase("Lanka Electronics")).findFirst().orElse(null);

        if (slt != null && userRepo.findByUsername("slt_admin").isEmpty()) {
            User ca = new User();
            ca.setUsername("slt_admin"); ca.setFullName("SLT Admin"); ca.setRole(User.Role.COMPANY_ADMIN);
            ca.setCompanyId(slt.getId()); ca.setCompanyName(slt.getName());
            ca.setPassword(encoder.encode("Company@123")); ca.setEnabled(true);
            userRepo.save(ca);
            System.out.println("SEEDER: Created slt_admin (Company@123)");
        }

        // Add Adeesha for Forgot Password testing
        if (userRepo.findByUsername("adeesha").isEmpty()) {
            User u = new User();
            u.setUsername("adeesha"); u.setFullName("Adeesha"); u.setRole(User.Role.EMPLOYEE);
            u.setEmail("adeeshahimal2002@gmail.com"); u.setPassword(encoder.encode("Temp@123"));
            u.setCompanyName("ABC Garments"); u.setEnabled(true);
            userRepo.save(u);
            System.out.println("SEEDER: Created test user 'adeesha' with email adeeshahimal2002@gmail.com");
        }

        if (le != null && userRepo.findByUsername("le_admin").isEmpty()) {
            User ca = new User();
            ca.setUsername("le_admin"); ca.setFullName("LE Admin"); ca.setRole(User.Role.COMPANY_ADMIN);
            ca.setCompanyId(le.getId()); ca.setCompanyName(le.getName());
            ca.setPassword(encoder.encode("Company@123")); ca.setEnabled(true);
            userRepo.save(ca);
            System.out.println("SEEDER: Created le_admin (Company@123)");
        }

        if (slt != null && userRepo.findByUsername("slt_tech").isEmpty()) {
            User du = new User();
            du.setUsername("slt_tech"); du.setFullName("SLT Technical Portal"); du.setRole(User.Role.DEPT_USER);
            du.setDepartment("Technical Support");
            du.setCompanyId(slt.getId()); du.setCompanyName(slt.getName());
            du.setPassword(encoder.encode("Dept@123")); du.setEnabled(true);
            userRepo.save(du);
            System.out.println("SEEDER: Created slt_tech (Dept@123)");
        }

        if (slt != null && userRepo.findByUsername("slt_finance").isEmpty()) {
            User du = new User();
            du.setUsername("slt_finance"); du.setFullName("SLT Finance Portal"); du.setRole(User.Role.DEPT_USER);
            du.setDepartment("Finance");
            du.setCompanyId(slt.getId()); du.setCompanyName(slt.getName());
            du.setPassword(encoder.encode("Dept@123")); du.setEnabled(true);
            userRepo.save(du);
            System.out.println("SEEDER: Created slt_finance (Dept@123)");
        }

        if (le != null && userRepo.findByUsername("le_tech").isEmpty()) {
            User du = new User();
            du.setUsername("le_tech"); du.setFullName("LE Technical Portal"); du.setRole(User.Role.DEPT_USER);
            du.setDepartment("Electronics Support");
            du.setCompanyId(le.getId()); du.setCompanyName(le.getName());
            du.setPassword(encoder.encode("Dept@123")); du.setEnabled(true);
            userRepo.save(du);
            System.out.println("SEEDER: Created le_tech (Dept@123)");
        }
    }

    private void seedVerificationData(CompanyEmployeeRepository repo) {
        repo.deleteAll();
        String[][] raw = {
            {"Adeesha", "554433221V", "EMP-ADE-001", "ABC Garments"},
            {"Nimal Perera", "123456789V", "EMP-001", "SLT Digital"},
            {"Sunil Silva", "987654321V", "EMP-002", "Lanka Electronics"},
            {"Kamala J.", "112233445V", "EMP-003", "QuickShop.lk"},
            {"D. Perera", "556677889V", "EMP-004", "ABC Garments"}
        };
        for (String[] r : raw) {
            CompanyEmployee ce = new CompanyEmployee();
            ce.setName(r[0]); ce.setNic(r[1]); ce.setCompanyEmployeeId(r[2]); ce.setCompanyName(r[3]);
            repo.save(ce);
        }
    }

    private void syncAdminProfiles(UserRepository userRepo, PasswordEncoder encoder) {
        userRepo.findAll().forEach(u -> {
            if (u.getRole() == User.Role.ADMIN || u.getRole() == User.Role.SUPER_ADMIN) {
                boolean changed = false;
                if (u.getPhone() == null || u.getPhone().isBlank()) {
                    u.setPhone("+94 77 " + (1110000 + (int)(u.getId() % 9) * 111111));
                    changed = true;
                }
                if (u.getDepartment() == null || u.getDepartment().isBlank()) {
                    u.setDepartment(u.getRole() == User.Role.SUPER_ADMIN ? "Head Office" : "Admin");
                    changed = true;
                }
                if (u.getEmail() == null || u.getEmail().isBlank() || !u.getEmail().contains("@gmail.com")) {
                   u.setEmail(u.getUsername().replace("admin_", "") + ".cms@gmail.com");
                   changed = true;
                }
                if (u.getProfileImageUrl() == null || u.getProfileImageUrl().isBlank()) {
                    if (u.getRole() == User.Role.SUPER_ADMIN) {
                        u.setProfileImageUrl("assets/braveena_profile.png");
                    } else {
                        u.setProfileImageUrl("https://ui-avatars.com/api/?name=" + u.getFullName().replace(" ", "+") + "&background=random");
                    }
                    changed = true;
                }
                if (changed) userRepo.save(u);
            }
        });
    }

    private void seedComplaints(UserRepository userRepo, ComplaintRepository complaintRepo) {
        User emp = userRepo.findByUsername("sahan").orElse(null); // Assuming Sahan was created elsewhere or exists
        // If sahan doesn't exist, try to find any employee
        if (emp == null) emp = userRepo.findByRole(User.Role.EMPLOYEE).stream().findFirst().orElse(null);
        
        // If still null, create a temp one 
        if (emp == null) {
            emp = new User();
            emp.setUsername("emp_sahan"); emp.setFullName("Sahan Perera"); emp.setRole(User.Role.EMPLOYEE);
            emp.setCompanyName("ABC Garments"); emp.setCompanyId(1L);
            emp = userRepo.save(emp);
        }

        User cust = userRepo.findByUsername("kavindi").orElse(null);
        if (cust == null) {
            cust = new User();
            cust.setUsername("cust_kavindi"); cust.setFullName("Kavindi"); cust.setRole(User.Role.CUSTOMER);
            cust = userRepo.save(cust);
        }

        List<User> admins = userRepo.findByRole(User.Role.ADMIN);
        User a1 = admins.size() > 0 ? admins.get(0) : null;
        User a2 = admins.size() > 1 ? admins.get(1) : a1;

        // Seed 10 Complaints
        createSeed(complaintRepo, "Salary Delay", "March salary not received", "Finance", "EMPLOYEE", emp, 101L, "Global Garments", null, Complaint.Status.PENDING, "HIGH", null);
        createSeed(complaintRepo, "Harassment", "Verbal abuse in meeting", "HR", "EMPLOYEE", emp, 101L, "Global Garments", a1, Complaint.Status.APPROVED, "HIGH", "HR Dept");
        createSeed(complaintRepo, "Damaged Laptop", "Laptop screen cracked on delivery", "Service", "CUSTOMER", cust, 202L, "Lanka Electronics", a2, Complaint.Status.VIEWED, "MEDIUM", "Tech Support");
        createSeed(complaintRepo, "Service Interruption", "Internet down for 3 days", "Tech", "CUSTOMER", cust, 203L, "SLT Digital", a1, Complaint.Status.IN_PROGRESS, "HIGH", "Network");
        createSeed(complaintRepo, "Payroll Error", "OT hours not calculated correctly", "Finance", "EMPLOYEE", emp, 101L, "Global Garments", a2, Complaint.Status.REJECTED, "MEDIUM", "Payroll");
        createSeed(complaintRepo, "Leave Denied", "Marriage leave rejected", "HR", "EMPLOYEE", emp, 101L, "Global Garments", a1, Complaint.Status.RECEIVED_RESOLUTION, "MEDIUM", null);
        createSeed(complaintRepo, "Refund Pending", "Refund for order #1234 still pending", "Billing", "CUSTOMER", cust, 202L, "Lanka Electronics", null, Complaint.Status.PENDING, "MEDIUM", null);
        createSeed(complaintRepo, "Safety Hazard", "Exposed wires in production floor", "Security", "EMPLOYEE", emp, 101L, "Global Garments", a2, Complaint.Status.RESOLUTION_REJECTED, "HIGH", "Maintenance");
        createSeed(complaintRepo, "Unhelpful Agent", "Support call was very rude", "Service", "CUSTOMER", cust, 203L, "SLT Digital", a1, Complaint.Status.APPROVED, "LOW", "Customer Care");
        createSeed(complaintRepo, "Wrong Invoice", "Charged for premium instead of basic", "Billing", "CUSTOMER", cust, 202L, "Lanka Electronics", a2, Complaint.Status.IN_PROGRESS, "MEDIUM", "Billing Dept");
    }

    // Helper for seeding complaints
    private Complaint createSeed(ComplaintRepository repo, String title, String desc, String cat, String type, User complainant, Long companyId, String companyName, User admin, Complaint.Status status, String priority, String dept) {
        Complaint c = new Complaint();
        c.setTitle(title); c.setDescription(desc); c.setCategory(cat); c.setComplainantType(type);
        c.setComplainantId(complainant.getId()); c.setComplainantName(complainant.getFullName());
        c.setCompanyId(companyId); c.setCompanyName(companyName);
        c.setAssignedAdminId(admin != null ? admin.getId() : null);
        c.setDepartment(dept); c.setStatus(status); c.setPriority(priority);
        c.setCreatedAt(LocalDateTime.now().minusDays((int)(Math.random() * 5)));
        return repo.save(c);
    }
}
