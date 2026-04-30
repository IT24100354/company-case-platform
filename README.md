# Multi-Company Complaint Management System

A full-stack, web-based Complaint Management System designed to streamline the process of reporting, tracking, and resolving complaints across multiple companies. The system supports multiple user roles, secure authentication, real-time communication, and AI-powered insights to improve administrative efficiency.

---

##  System Overview

This platform enables customers and employees to submit complaints, attach supporting evidence, and track progress, while administrators manage, assign, and resolve issues efficiently. The system ensures transparency, accountability, and structured communication between all parties.

---

## User Roles

### Super Admin
- Full system access and control
- Approve or reject company/admin registrations
- View all complaints across companies
- Assign complaints to admins
- Monitor system-wide activity

### Admin
- Manage assigned complaints
- Forward complaints to companies/departments
- Update complaint status
- Communicate with customers, employees, and super admin

### Customer
- Submit complaints related to services/products
- Upload evidence (documents, images, videos, audio)
- Track complaint status
- Communicate with admins

### Employee
- Submit internal complaints within a company
- Verified using Employee ID and NIC
- Track complaint progress
- Communicate with admins

---

## Key Features

### Complaint Management
- Create complaints with title, description, and category
- Select company and department
- Upload multiple types of evidence
- Track full complaint lifecycle:
  - `PENDING → APPROVED → IN_PROGRESS → RESOLVED`

---

### Evidence Handling
- Supports:
  - Documents (PDF, DOCX)
  - Images (JPG, PNG)
  - Videos (MP4)
  - Audio recordings
- Files are stored securely and linked to complaints

---

### AI-Powered Complaint Summary
- Automatically generates short summaries from long complaints
- Uses OpenAI API for natural language processing
- Reads document-based evidence using Apache Tika
- Improves admin decision-making speed

---

### Communication System
- Real-time chat per complaint
- Supports:
  - Admin ↔ Customer
  - Admin ↔ Employee
  - Admin ↔ Super Admin
- Keeps all discussions centralized

---

### Notification System
- Alerts for:
  - New complaints
  - Status updates
  - New messages
- Ensures timely responses

---

### Authentication & Security
- Secure login using Spring Security
- Password encryption using BCrypt
- Role-Based Access Control (RBAC)
- Google Sign-In integration (optional)

---

### Multi-Company Support
- Supports multiple companies in one system
- Each complaint linked to a specific company
- Company-based complaint routing

---

## Technologies Used

### Backend
- Java (Spring Boot)
- Spring Security
- Spring Data JPA / Hibernate

### Frontend
- HTML5
- CSS3
- JavaScript (Vanilla)

### Database
- MySQL

### AI & Processing
- OpenAI API (GPT)
- Apache Tika (document text extraction)

### Tools
- Git & GitHub
- IntelliJ IDEA


project-root/
│
├── backend/ # Spring Boot application
├── frontend/ # HTML, CSS, JS UI
├── uploads/ # Evidence files (ignored in Git)
├── README.md
└── .gitignore
