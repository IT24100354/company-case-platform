/**
 * COMPLAINT MANAGEMENT SYSTEM - FULL SCHEMA RESET
 * Point of contact: MySQL Workbench
 */

-- 1. DATABASE SETUP
DROP DATABASE IF EXISTS complaint_platform_db;
CREATE DATABASE complaint_platform_db;
USE complaint_platform_db;

-- 2. CREATE TABLES

-- Table: admin_users
CREATE TABLE admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BIT(1) DEFAULT 1,
    name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    department VARCHAR(100),
    join_date VARCHAR(50),
    profile_pic LONGTEXT,
    first_login BIT(1) DEFAULT 0
);

-- Table: admin_assigned_companies (Mapping table for Admin Assigned Companies)
CREATE TABLE admin_assigned_companies (
    admin_user_id BIGINT NOT NULL,
    company_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (admin_user_id, company_id),
    FOREIGN KEY (admin_user_id) REFERENCES admin_users(id) ON DELETE CASCADE
);

-- Table: complaints
CREATE TABLE complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(255),
    employee_id VARCHAR(50),
    employee_name VARCHAR(255),
    employee_email VARCHAR(255),
    employee_dept VARCHAR(100),
    company_id VARCHAR(50),
    company_name VARCHAR(255),
    assigned_admin_id VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(50),
    due_date VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP
);

-- Table: forwarded_complaints
CREATE TABLE forwarded_complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id BIGINT,
    title VARCHAR(255),
    description TEXT,
    forwarded_to_company VARCHAR(255),
    forwarded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE SET NULL
);

-- 3. INSERT SAMPLE DATA

-- Admins: Braveena (Super Admin) & Murugan (Admin)
INSERT INTO admin_users (username, password, role, enabled, name, email, phone, department, join_date)
VALUES 
('braveena', 'super@123', 'SUPER_ADMIN', 1, 'Braveena Selvam', 'sbbraveena@gmail.com', '+91-9876543210', 'Administration', '2024-01-01'),
('murugan', 'admin@123', 'ADMIN', 1, 'Murugan Rajan', 'murugan14@gmail.com', '+91-9845123456', 'Complaint Management', '2024-03-15');

-- Assigning Companies to Murugan
INSERT INTO admin_assigned_companies (admin_user_id, company_id)
VALUES (2, 'CO-001'), (2, 'CO-004');

-- Sample Complaints
INSERT INTO complaints (title, description, category, employee_id, employee_name, employee_email, employee_dept, company_id, company_name, assigned_admin_id, status, priority, due_date)
VALUES 
('Workplace Harassment by Supervisor', 'I have been subjected to repeated verbal harassment by my supervisor over the past three months.', 'Harassment', 'EMP-101', 'Priya Annamalai', 'priya.a@techsol.in', 'Human Resources', 'CO-001', 'TechSolutions Pvt. Ltd.', 'ADM-001', 'PENDING', 'HIGH', NULL),
('Unfair Dismissal Without Notice', 'My employment was terminated without the legally required notice period and without severance pay.', 'Unfair Dismissal', 'EMP-202', 'Karthikeyan Pandi', 'karthik.p@globallogistics.in', 'Operations', 'CO-002', 'Global Logistics Co.', 'ADM-002', 'PENDING', 'MEDIUM', NULL),
('Wage Theft – Unpaid Overtime Hours', 'I have been working 3–4 hours of daily overtime without compensation for 2 months.', 'Wage Dispute', 'EMP-303', 'Meenakshi Rajan', 'meenakshi.r@techsol.in', 'Retail Sales', 'CO-001', 'TechSolutions Pvt. Ltd.', 'ADM-001', 'FORWARDED', 'MEDIUM', '2026-04-15');

-- One Forwarded Case Record
INSERT INTO forwarded_complaints (complaint_id, title, description, forwarded_to_company)
VALUES (3, 'Wage Theft – Unpaid Overtime Hours', 'I have been working 3–4 hours of daily overtime without compensation for 2 months.', 'TechSolutions Pvt. Ltd.');
