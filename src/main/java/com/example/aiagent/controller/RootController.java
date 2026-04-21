package com.example.aiagent.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aiagent.agents.mailing.AgentMailing;
import com.example.aiagent.agents.site.AgentSite;
import com.example.aiagent.agents.social.AgentSM;
import com.example.aiagent.core.creation.CreationService;
import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.dto.RootRequest;
import com.example.aiagent.entity.ChatHistory;
import com.example.aiagent.repository.ChatHistoryRepository;

@RestController
@RequestMapping("/api/root")
public class RootController {

    @Autowired private AgentSite agentSite;
    @Autowired private AgentSM agentSM;
    @Autowired private AgentMailing agentMailing;
    @Autowired private CreationService creationService;
    @Autowired private ChatHistoryRepository chatHistoryRepository;

    @PostMapping("/process")
    public ChatResponse process(@RequestBody RootRequest request) {
        String prompt = request.getPrompt();
        String lower = prompt.toLowerCase();
        Map<String, Object> metadata = request.getMetadata();
        List<ChatResponse.ProcessedResult> results = new ArrayList<>();

        String finalLanguage;
        if (metadata.containsKey("language") && metadata.get("language") != null) {
            finalLanguage = metadata.get("language").toString();
        } else {
            try {
                finalLanguage = creationService.generateText(
                    "Detect language (ex: 'français', 'arabe', 'anglais'). ONLY the word.",
                    prompt
                ).toLowerCase().replaceAll("[^a-z]", "");
            } catch(Exception e) { finalLanguage = "français"; }
        }
        metadata.put("language", finalLanguage);

        // 2. MOT-CLÉ VISUEL (Zéro Hallucination / Zéro Chat)
        String visualTopic = "technology";
        try {
            String topicResponse = creationService.generateText(
                "Give me only ONE simple English noun that represents the visual topic of: " + prompt + ". Never say 'chat' or 'text'.",
                "Example: 'car', 'pizza', 'building'."
            ).toLowerCase().replaceAll("[^a-z]", "").trim();
            
            if (!topicResponse.isEmpty() && !topicResponse.contains("chat")) {
                visualTopic = topicResponse;
            }
        } catch(Exception e) { visualTopic = "tech"; }
        
        System.out.println("DEBUG: Mot-clé visuel extrait = " + visualTopic);
        metadata.put("visualTopic", visualTopic);

        // 3. AGENTS
        String aiResult;
        String agentType;

        if (lower.contains("site") || lower.contains("page") || lower.contains("landing") || lower.contains("hero") || lower.contains("contenu") || lower.contains("section")) {
            aiResult = agentSite.buildSite(prompt, "LP", metadata);
            agentType = "AGENT_SITE";
        } else if (lower.contains("social") || lower.contains("instagram") || lower.contains("facebook") || lower.contains("linkedin") || lower.contains("post") || lower.contains("reel")) {
            aiResult = agentSM.processSocialMedia(prompt, "Social", metadata);
            agentType = "AGENT_SM";
        } else if (lower.contains("mail") || lower.contains("email")) {
            aiResult = agentMailing.processMailing(prompt, metadata);
            agentType = "AGENT_MAILING";
        } else {
            aiResult = "Agent non déterminé.";
            agentType = "UNKNOWN_AGENT";
        }

        aiResult = cleanMarkdown(aiResult);
        results.add(new ChatResponse.ProcessedResult(agentType, aiResult, Map.copyOf(metadata)));

        ChatHistory history = new ChatHistory();
        history.setUserMessage(prompt);
        history.setAiResponse(aiResult);
        history.setAgentType(agentType);
        history.setMetadata(metadata.toString()); 
        history.setTimestamp(LocalDateTime.now());
        chatHistoryRepository.save(history);

        ChatResponse response = new ChatResponse();
        response.setStatus("SUCCESS");
        response.setPreviewUrl("http://localhost:8080/api/root/preview");
        response.setDetailedResults(results);
        return response;
    }

    @GetMapping("/preview")
    public ResponseEntity<String> preview() {
        ChatHistory last = chatHistoryRepository.findFirstByOrderByIdDesc().orElse(null);
        if (last == null) return ResponseEntity.notFound().build();

        String content = last.getAiResponse();
        String metadataStr = last.getMetadata();
        String prompt = last.getUserMessage().toLowerCase();
        
        String platform = "Instagram";
        if (metadataStr.toLowerCase().contains("platform=linkedin") || prompt.contains("linkedin")) platform = "LinkedIn";
        else if (metadataStr.toLowerCase().contains("platform=facebook") || prompt.contains("facebook")) platform = "Facebook";

        boolean isArabic = metadataStr.toLowerCase().contains("arabe") || metadataStr.toLowerCase().contains("arabic") || prompt.contains("arabe") || prompt.contains("ar");

        String brandingName = "Inject_IA";
        if (metadataStr.contains("branding=")) {
            String sub = metadataStr.substring(metadataStr.indexOf("branding=") + 9);
            brandingName = sub.split(",")[0].replace("}", "").replace("{", "").trim();
        }

        String keywords = "technology";
        if (metadataStr.contains("visualTopic=")) {
            String sub = metadataStr.substring(metadataStr.indexOf("visualTopic=") + 12);
            keywords = sub.split(",")[0].replace("}", "").replace("{", "").trim();
        }

        String imageUrl = "https://loremflickr.com/1080/1080/" + keywords;

        if (content.contains("<!DOCTYPE html>")) {
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.valueOf("text/html;charset=UTF-8")).body(content);
        }

        StringBuilder html = new StringBuilder();
        String direction = isArabic ? "rtl" : "ltr";
        String textAlign = isArabic ? "text-right" : "text-left";

        html.append("<!DOCTYPE html><html lang='").append(direction).append("' dir='").append(direction).append("'><head><meta charset='UTF-8'><script src='https://cdn.tailwindcss.com'></script><title>Preview</title></head>");
        
        if (platform.equals("Instagram")) {
            html.append("<body class='bg-[#fafafa] flex items-center justify-center min-h-screen p-4'>");
            html.append("<div class='w-[360px] bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden ").append(textAlign).append("'>");
            html.append("  <div class='p-3 flex items-center gap-2'><div class='w-8 h-8 rounded-full bg-gradient-to-tr from-yellow-400 to-purple-600 p-[1px]'><div class='w-full h-full rounded-full bg-gray-200 border-white border'></div></div><span class='font-bold text-xs'>").append(brandingName).append("</span></div>");
            html.append("  <img src='").append(imageUrl).append("' class='w-full aspect-square object-cover'>");
            html.append("  <div class='p-3 flex gap-4 text-gray-800'>");
            html.append("    <svg class='w-6 h-6' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z'></path></svg>");
            html.append("  </div>");
            html.append("  <div class='px-3 pb-6 text-[13px] text-gray-900'><span class='font-bold mr-1'>").append(brandingName).append("</span>").append(content).append("</div>");
            html.append("</div>");
        } else {
            html.append("<body class='bg-[#f3f6f8] flex items-center justify-center min-h-screen p-4'>");
            html.append("<div class='w-[400px] bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden ").append(textAlign).append("'>");
            html.append("  <div class='p-3 flex items-center gap-2 border-b'><div class='w-9 h-9 rounded-sm flex items-center justify-center text-white font-bold text-xs ").append(platform.equals("LinkedIn") ? "bg-[#0077b5]" : "bg-[#1877f2]").append("'>").append(platform.substring(0,2)).append("</div><div><p class='font-bold text-xs'>").append(brandingName).append("</p><p class='text-[10px] text-gray-500'>").append(platform).append(" • Sponsors</p></div></div>");
            html.append("  <div class='p-3 text-[13px] whitespace-pre-wrap text-gray-800'>").append(content).append("</div>");
            html.append("  <img src='").append(imageUrl).append("' class='w-full aspect-video object-cover'>");
            html.append("  <div class='p-3 border-t flex justify-around text-xs font-semibold text-gray-500'><span>Like</span><span>Comment</span><span>Share</span></div>");
            html.append("</div>");
        }
        
        html.append("</body></html>");

        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.valueOf("text/html;charset=UTF-8")).body(html.toString());
    }

    private String cleanMarkdown(String text) {
        if (text == null) return text;
        String cleaned = text.trim();
        if (cleaned.startsWith("```html")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
}
