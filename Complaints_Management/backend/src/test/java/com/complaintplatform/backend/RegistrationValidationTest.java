package com.complaintplatform.backend;

import com.complaintplatform.backend.controller.UnifiedAuthController;
import com.complaintplatform.backend.model.User;
import com.complaintplatform.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RegistrationValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    public void testDuplicateRegistrationNumberFails() throws Exception {
        String duplicateRegNum = "ABC123";
        
        // Mock that the registration number already exists in DB
        User existingUser = new User();
        existingUser.setRegistrationNumber(duplicateRegNum);
        
        Mockito.when(userRepository.findByRegistrationNumber(duplicateRegNum))
               .thenReturn(Optional.of(existingUser));

        Map<String, String> payload = new HashMap<>();
        payload.put("role", "COMPANY_ADMIN");
        payload.put("username", "new_user");
        payload.put("password", "Password123");
        payload.put("fullName", "Test User");
        payload.put("registrationNumber", duplicateRegNum);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Company registration number already exists."));
    }
}
