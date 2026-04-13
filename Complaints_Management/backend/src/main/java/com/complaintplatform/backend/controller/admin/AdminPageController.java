package com.complaintplatform.backend.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping("/admin/complaints")
    public String complaintsPage() {
        return "redirect:/admin-complaint-detail.html";
    }
}
