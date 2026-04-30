package com.complaintplatform.backend.service;

import com.complaintplatform.backend.model.Complaint;
import com.complaintplatform.backend.model.Evidence;
import com.complaintplatform.backend.repository.EvidenceRepository;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final EvidenceRepository evidenceRepo;
    private final Tika tika = new Tika();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${cms.upload.path}")
    private String uploadDir;

    @Value("${openai.api.key:EMPTY}")
    private String apiKey;

    public AiService(EvidenceRepository evidenceRepo) {
        this.evidenceRepo = evidenceRepo;
    }

    public String generateSummary(Complaint complaint) {
        StringBuilder context = new StringBuilder();
        context.append("Title: ").append(complaint.getTitle()).append("\n");
        context.append("Description: ").append(complaint.getDescription()).append("\n");

        System.out.println("DEBUG: AI Summary Generation for Complaint #" + complaint.getId());
        
        List<Evidence> evidenceList = evidenceRepo.findByCaseId(String.valueOf(complaint.getId()));
        if (!evidenceList.isEmpty()) {
            context.append("\nEvidence Analysis from Attached Documents:\n");
            for (Evidence ev : evidenceList) {
                String text = extractText(ev);
                if (text != null && !text.isBlank()) {
                    System.out.println("DEBUG: Extracted " + text.length() + " chars from " + ev.getFileName());
                    context.append("[Content of Evidence File: ").append(ev.getFileName()).append("]:\n")
                           .append(text.substring(0, Math.min(text.length(), 3000))).append("\n\n");
                } else {
                    System.out.println("DEBUG: No text extracted from " + ev.getFileName());
                    context.append("[Note: Evidence File attached: ").append(ev.getFileName())
                           .append(" (Type: ").append(ev.getFileType()).append(")] - Supporting document provided but text content not extractable.\n");
                }
            }
        } else {
            System.out.println("DEBUG: No evidence files found for Complaint #" + complaint.getId());
        }

        return callAiApi(context.toString());
    }

    private String extractText(Evidence ev) {
        try {
            String filePath = ev.getFilePath();
            // Robust path handling for both "uploads/..." and "/uploads/..."
            String fileName = filePath;
            if (fileName.startsWith("/uploads/")) {
                fileName = fileName.substring(9);
            } else if (fileName.startsWith("uploads/")) {
                fileName = fileName.substring(8);
            }
            
            Path path = Paths.get(uploadDir).resolve(fileName);
            File file = path.toFile();
            
            System.out.println("DEBUG: Processing Evidence: " + ev.getFileName());
            System.out.println("DEBUG: Resolved Path: " + path.toAbsolutePath());
            System.out.println("DEBUG: File Exists: " + file.exists());

            if (file.exists()) {
                // Tika will automatically detect type and extract text from PDF, DOCX, TXT, etc.
                String extracted = tika.parseToString(file);
                return extracted;
            }
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to extract text from " + ev.getFileName() + ": " + e.getMessage());
        }
        return null;
    }

    private String callAiApi(String promptContent) {
        if ("EMPTY".equals(apiKey)) {
            // Fallback if no real API key is provided
            return simulateAiSummary(promptContent);
        }

        try {
            String url = "https://api.openai.com/v1/chat/completions";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-3.5-turbo");
            body.put("messages", List.of(
                Map.of("role", "system", "content", "Summarize this complaint for an admin dashboard in 1–2 short, professional, easy-to-understand sentences. Focus on the core issue and requested resolution."),
                Map.of("role", "user", "content", promptContent)
            ));
            body.put("max_tokens", 150);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            System.err.println("AI API call failed: " + e.getMessage());
        }

        return simulateAiSummary(promptContent);
    }

    private String simulateAiSummary(String content) {
        // Fallback logic that acknowledges evidence if present in the prompt
        String title = "";
        String desc = "";
        boolean hasEvidenceText = content.contains("[Content of Evidence File:");
        boolean hasEvidenceAttached = content.contains("[Note: Evidence File attached:");
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("Title: ")) title = line.substring(7);
            if (line.startsWith("Description: ")) desc = line.substring(13);
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("The complaint '").append(title).append("' regarding ").append(desc.length() > 60 ? desc.substring(0, 60) + "..." : desc);
        
        if (hasEvidenceText) {
            summary.append(" is supported by details found in the attached documents, indicating a mismatch or discrepancy.");
        } else if (hasEvidenceAttached) {
            summary.append(". Supporting documents are attached, though their text could not be fully analyzed.");
        } else {
            summary.append(". No additional evidence documents were found.");
        }
        
        return "AI Summary (Local Gen): " + summary.toString();
    }
}
