package com.example.aiagent.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestParam;

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
import com.example.aiagent.core.document.DriveWatcherService;
import com.example.aiagent.core.rate.RateLimiterService;
import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.dto.RootRequest;
import com.example.aiagent.entity.ChatHistory;
import com.example.aiagent.repository.ChatHistoryRepository;

@RestController
@RequestMapping("/api/root")
public class RootController {

    private final AgentSite agentSite;
    private final AgentSM agentSM;
    private final AgentMailing agentMailing;
    private final CreationService creationService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final DriveWatcherService driveWatcherService;
    private final RateLimiterService rateLimiterService;

    public RootController(AgentSite agentSite, AgentSM agentSM, AgentMailing agentMailing,
                         CreationService creationService, ChatHistoryRepository chatHistoryRepository,
                         DriveWatcherService driveWatcherService, RateLimiterService rateLimiterService) {
        this.agentSite = agentSite;
        this.agentSM = agentSM;
        this.agentMailing = agentMailing;
        this.creationService = creationService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.driveWatcherService = driveWatcherService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/process")
    public ChatResponse process(@RequestBody RootRequest request) {
        String prompt = request.getPrompt();
        String lower = prompt.toLowerCase();
        Map<String, Object> metadata = request.getMetadata();
        // Ensure metadata map non-null
        if (metadata == null) metadata = new HashMap<>();
        List<ChatResponse.ProcessedResult> results = new ArrayList<>();

        // 1. DÉTECTION AGENT — priorité au type explicite envoyé par le frontend
        String agentType;
        String typeParam = metadata.containsKey("type") ? metadata.get("type").toString() : "";
        if (typeParam.equals("landing_page") || typeParam.equals("contenu")) {
            agentType = "AGENT_SITE";
        } else if (typeParam.equals("social")) {
            agentType = "AGENT_SM";
        } else if (typeParam.equals("mailing")) {
            agentType = "AGENT_MAILING";
        } else if (lower.contains("site") || lower.contains("page") || lower.contains("landing")
                || lower.contains("hero") || lower.contains("contenu") || lower.contains("section")
                || lower.contains("vitrine") || lower.contains("web")) {
            agentType = "AGENT_SITE";
        } else if (lower.contains("social") || lower.contains("instagram") || lower.contains("facebook")
                || lower.contains("linkedin") || lower.contains("twitter") || lower.contains("tweet")
                || lower.contains("post") || lower.contains("reel")) {
            agentType = "AGENT_SM";
        } else if (lower.contains("mail") || lower.contains("email")) {
            agentType = "AGENT_MAILING";
        } else {
            agentType = "AGENT_SITE"; // défaut : landing page
        }

        // 2. LANGUE — défaut français (évite un appel IA inutile)
        String finalLanguage = metadata.containsKey("language") && metadata.get("language") != null
                ? metadata.get("language").toString() : "français";
        metadata.put("language", finalLanguage);

        // requestId: prefer frontend-provided id for idempotency / dedup
        String requestId = metadata.containsKey("requestId") && metadata.get("requestId") != null
            ? metadata.get("requestId").toString() : java.util.UUID.randomUUID().toString();
        metadata.put("requestId", requestId);
        // expose agentType in metadata for downstream tracing
        metadata.put("agentType", agentType);

        // Rate limiting: prefer authenticated user email if present, otherwise use requestId
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication() != null
            ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        String rateKey = currentUserEmail != null ? currentUserEmail : requestId;
        boolean allowed = rateLimiterService.allowRequest(rateKey);
        if (!allowed) {
            ChatResponse resp = new ChatResponse();
            resp.setStatus("RATE_LIMITED");
            resp.setRequestId(requestId);
            resp.setPreviewUrl("");
            resp.setDetailedResults(List.of());
            System.out.println("DEBUG: Rate limited requestId=" + requestId + " key=" + rateKey);
            return resp;
        }

        // 3. VISUAL TOPIC & MEDIA: media generation must be explicitly requested
        boolean generateMedia = Boolean.parseBoolean(metadata.getOrDefault("generateMedia", "false").toString());
        String visualTopic = metadata.containsKey("visualTopic") ? metadata.get("visualTopic").toString() : "people,business";
        boolean needsImage = generateMedia && !agentType.equals("AGENT_SITE") && !agentType.equals("UNKNOWN_AGENT");
        // store back (may be used later by front/backend)
        metadata.put("visualTopic", visualTopic);

        // 4. INJECTION CONTEXTE CLIENT
        String enrichedPrompt = prompt;
        if (metadata.containsKey("clientId") && metadata.get("clientId") != null
                && Boolean.parseBoolean(metadata.getOrDefault("includeClientContext", "false").toString())) {
            String clientId = metadata.get("clientId").toString();
            String context = driveWatcherService.buildContext(clientId);
            if (!context.isBlank()) {
                // Truncate context to avoid prompt explosion
                int maxContext = 2000;
                String shortContext = context.length() > maxContext ? context.substring(0, maxContext) + "\n[TRUNCATED CONTEXT]" : context;
                enrichedPrompt = prompt + "\n\n--- EXPERTISE CLIENT ---\n" + shortContext;
                System.out.println("DEBUG: Contexte client injecté (truncated) pour " + clientId + " requestId=" + requestId);
            }
        }

        // ensure enrichedPrompt not too long
        int maxPrompt = 4000;
        if (enrichedPrompt.length() > maxPrompt) {
            enrichedPrompt = enrichedPrompt.substring(0, maxPrompt) + "\n[TRUNCATED_PROMPT]";
            System.out.println("DEBUG: enrichedPrompt truncated requestId=" + requestId + " length=" + enrichedPrompt.length());
        }

        // 5. APPEL AGENT
        String aiResult;
        switch (agentType) {
            case "AGENT_SITE" -> aiResult = agentSite.buildSite(enrichedPrompt, "LP", metadata);
            case "AGENT_SM" -> aiResult = agentSM.processSocialMedia(enrichedPrompt, "Social", metadata);
            case "AGENT_MAILING" -> aiResult = agentMailing.processMailing(enrichedPrompt, metadata);
            default -> aiResult = "Agent non déterminé.";
        }
        // After agent call, log LLM call count observed for this requestId
        int llmCalls = creationService.getCallCount(requestId);
        int estimatedTokens = creationService.getEstimatedTokens(requestId);
        System.out.println("DEBUG: requestId=" + requestId + " agentType=" + agentType + " llmCalls=" + llmCalls + " estimatedTokens=" + estimatedTokens);
        // clear per-request counter for next use
        creationService.clearCallCount(requestId);
        aiResult = cleanMarkdown(aiResult);

        // 6. IMAGE / VIDÉO (non-site uniquement)
        String imageUrl = "";
        String videoUrl = "";
        boolean isReel = (metadata.containsKey("type") && "reel".equalsIgnoreCase(metadata.get("type").toString()))
                      || lower.contains("reel") || lower.contains("video") || lower.contains("vidéo");
        if (needsImage) {
            // Only generate media if explicitly requested via metadata.generateMedia = true and visualTopic present
            if (visualTopic != null && !visualTopic.isBlank()) {
                if (isReel) {
                    videoUrl = creationService.generateVideo(visualTopic);
                    metadata.put("videoUrl", videoUrl);
                } else {
                    imageUrl = creationService.generateImage(visualTopic);
                    metadata.put("imageUrl", imageUrl);
                }
            }
        }

        // 5. GÉNÉRATION LOGO (SVG moderne — icône géométrique + wordmark professionnel)
        if (agentType.equals("AGENT_SM") && metadata.containsKey("branding") && metadata.get("branding") != null && !metadata.get("branding").toString().isBlank()) {
            String brand = metadata.get("branding").toString().trim();
            String[] words = brand.split("\\s+");
            String initial = String.valueOf(words[0].charAt(0)).toUpperCase();

            int hash = Math.abs(brand.hashCode());
            String[][] gradients = {
                {"#6C63FF","#3ECFCF"}, // violet-cyan
                {"#FF6B6B","#FFE66D"}, // rouge-jaune
                {"#4ECDC4","#2196F3"}, // teal-bleu
                {"#FF9A9E","#A18CD1"}, // rose-lilas
                {"#43E97B","#38F9D7"}, // vert-menthe
                {"#F7971E","#FFD200"}, // orange-or
                {"#4776E6","#8E54E9"}, // bleu-violet
                {"#F953C6","#B91D73"}  // fuchsia-rose
            };
            String[] grad = gradients[hash % gradients.length];
            String gradId = "lg" + (hash % 9999);
            String shadowId = "sh" + (hash % 9999);

            // Largeur dynamique — 13px par caractère bold + marges généreuses
            int logoWidth = 60 + brand.length() * 13;
            int iconSize = 40;
            int textX = iconSize + 12;
            int textY = 25;
            int totalHeight = 44;

            // Forme de l'icône : cercle avec lettre (style moderne)
            String svg = "<?xml version='1.0' encoding='UTF-8'?>"
                + "<svg xmlns='http://www.w3.org/2000/svg' width='" + logoWidth + "' height='" + totalHeight + "' viewBox='0 0 " + logoWidth + " " + totalHeight + "'>"
                + "<defs>"
                + "<linearGradient id='" + gradId + "' x1='0%' y1='0%' x2='100%' y2='100%'>"
                + "<stop offset='0%' stop-color='" + grad[0] + "'/>"
                + "<stop offset='100%' stop-color='" + grad[1] + "'/>"
                + "</linearGradient>"
                + "<filter id='" + shadowId + "' x='-20%' y='-20%' width='140%' height='140%'>"
                + "<feDropShadow dx='0' dy='2' stdDeviation='3' flood-color='" + grad[0] + "' flood-opacity='0.35'/>"
                + "</filter>"
                + "</defs>"
                // Icône cercle avec dégradé
                + "<circle cx='20' cy='22' r='20' fill='url(#" + gradId + ")' filter='url(#" + shadowId + ")'/>"
                // Lettre initiale dans le cercle
                + "<text x='20' y='22' dominant-baseline='central' text-anchor='middle' "
                + "font-family='Arial Black,Helvetica Neue,sans-serif' font-weight='900' font-size='19' fill='#fff'>"
                + initial + "</text>"
                // Nom de la marque à droite
                + "<text x='" + textX + "' y='" + textY + "' "
                + "font-family='Arial Black,Helvetica Neue,sans-serif' font-weight='800' font-size='17' "
                + "letter-spacing='-0.3' fill='#1a1a2e'>"
                + brand + "</text>"
                + "</svg>";

            String logoUrl = "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            metadata.put("logoUrl", logoUrl);
            System.out.println("DEBUG: logoUrl = Wordmark(" + brand + " | " + grad[0] + ")");
        }

        results.add(new ChatResponse.ProcessedResult(agentType, aiResult, Map.copyOf(metadata)));

        ChatHistory history = new ChatHistory();
        history.setUserMessage(prompt);
        history.setAiResponse(aiResult);
        history.setAgentType(agentType);
        history.setClientEmail(currentUserEmail);
        history.setMetadata(metadata.toString());
        history.setImageUrl(isReel ? videoUrl : imageUrl);
        history.setLanguage(finalLanguage);
        history.setTimestamp(LocalDateTime.now());
        chatHistoryRepository.save(history);

        ChatResponse response = new ChatResponse();
        response.setStatus("SUCCESS");
        response.setPreviewUrl("http://localhost:8080/api/root/preview");
        response.setRequestId(requestId);
        response.setLlmCallCount(llmCalls);
        response.setEstimatedTokens(estimatedTokens);
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
        else if (metadataStr.toLowerCase().contains("platform=twitter") || metadataStr.toLowerCase().contains("platform=x") || prompt.contains("twitter") || prompt.contains("tweet")) platform = "Twitter";

        boolean isArabic = metadataStr.toLowerCase().contains("arabe") || metadataStr.toLowerCase().contains("arabic") || prompt.contains("arabe") || prompt.contains("ar");

        String brandingName = "";
        if (metadataStr.contains("branding=")) {
            String sub = metadataStr.substring(metadataStr.indexOf("branding=") + 9);
            brandingName = sub.split(",")[0].replace("}", "").replace("{", "").trim();
        }

        String mediaUrl = last.getImageUrl() != null ? last.getImageUrl() : "";
        boolean isVideo = mediaUrl.endsWith(".mp4") || mediaUrl.contains("/videos/");
        String imageUrl = isVideo ? "" : mediaUrl;
        String videoUrl = isVideo ? mediaUrl : "";

        String logoUrl = "";
        if (metadataStr.contains("logoUrl=")) {
            // Extract until the next metadata key (pattern: ", word=") or end of map
            String sub = metadataStr.substring(metadataStr.indexOf("logoUrl=") + 8);
            int end = sub.indexOf(", imageUrl=");
            if (end == -1) end = sub.indexOf(", language=");
            if (end == -1) end = sub.indexOf("}");
            if (end > 0) logoUrl = sub.substring(0, end).trim();
            else logoUrl = sub.replace("}", "").trim();
        }

        if (content.contains("<!DOCTYPE html>")) {
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.valueOf("text/html;charset=UTF-8")).body(content);
        }

        StringBuilder html = new StringBuilder();
        String direction = isArabic ? "rtl" : "ltr";
        String textAlignStyle = isArabic ? "text-align:right" : "text-align:left";

        html.append("<!DOCTYPE html><html lang='").append(direction).append("' dir='").append(direction).append("'><head><meta charset='UTF-8'><title>Preview</title>");
        html.append("<style>");
        html.append("*{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;}");
        html.append("body{background:#fafafa;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:16px;}");
        html.append(".card{background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.12);}");
        html.append(".img-box{width:100%;position:relative;overflow:hidden;background:#e5e7eb;}");
        html.append(".img-box::before{content:'';display:block;padding-top:100%;}");
        html.append(".img-box.wide::before{padding-top:56.25%;}");
        html.append(".img-box img{position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;display:block;}");
        html.append("</style></head>");

        switch (platform) {
            case "Instagram" -> {
            html.append("<body><div class='card' style='width:360px;border:1px solid #dbdbdb;").append(textAlignStyle).append("'>");
            // Header avec logo wordmark
            html.append("<div style='padding:10px 12px;display:flex;align-items:center;gap:8px;'>");
            if (!logoUrl.isBlank()) {
                html.append("<img src='").append(logoUrl).append("' style='height:36px;width:auto;object-fit:contain;'>");
            } else {
                html.append("<div style='width:36px;height:36px;border-radius:50%;background:#c8c8c8;'></div>");
                html.append("<span style='font-weight:700;font-size:13px;'>").append(brandingName).append("</span>");
            }
            html.append("</div>");
            // Media (image ou vidéo)
            html.append("<div class='img-box'>");
            if (!videoUrl.isBlank()) {
                html.append("<video src='").append(videoUrl).append("' style='position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;' autoplay muted loop playsinline></video>");
            } else {
                html.append("<img src='").append(imageUrl).append("' onerror=\"this.src='https://picsum.photos/1080/1080'\">");
            }
            html.append("</div>");
            // Like
            html.append("<div style='padding:10px 12px;'>");
            html.append("<svg width='22' height='22' fill='none' stroke='#262626' stroke-width='2' viewBox='0 0 24 24'><path d='M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z'/></svg>");
            html.append("</div>");
            // Caption
            html.append("<div style='padding:0 12px 16px;font-size:13px;color:#262626;line-height:1.5;'>");
            html.append("<span style='font-weight:700;margin-right:4px;'>").append(brandingName).append("</span>").append(content);
            html.append("</div></div></body>");
            }
            case "Twitter" -> {
            // === TWITTER / X CARD ===
            // Avatar circulaire dédié (pas le wordmark complet)
            String[] twitterColors = {"#6C63FF","#FF6B6B","#4ECDC4","#FF9A9E","#43E97B","#F7971E","#4776E6","#F953C6"};
            String avatarColor = twitterColors[Math.abs(brandingName.hashCode()) % twitterColors.length];
            String avatarInitial = brandingName.isEmpty() ? "X" : String.valueOf(brandingName.charAt(0)).toUpperCase();
            String avatarSvg = "<svg xmlns='http://www.w3.org/2000/svg' width='42' height='42'>"
                + "<circle cx='21' cy='21' r='21' fill='" + avatarColor + "'/>"
                + "<text x='21' y='21' dominant-baseline='central' text-anchor='middle' "
                + "font-family='Arial Black,sans-serif' font-weight='900' font-size='18' fill='#fff'>"
                + avatarInitial + "</text></svg>";
            String avatarUrl = "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(avatarSvg.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            html.append("<body style='background:#000;'><div class='card' style='width:420px;background:#16181c;border:1px solid #2f3336;color:#e7e9ea;").append(textAlignStyle).append("'>");
            // Header : avatar rond + nom + @handle + logo X
            html.append("<div style='padding:12px 14px 8px;display:flex;align-items:flex-start;gap:10px;'>");
            html.append("<img src='").append(avatarUrl).append("' style='width:42px;height:42px;border-radius:50%;flex-shrink:0;'>");
            html.append("<div style='flex:1;min-width:0;'>");
            html.append("<div style='display:flex;justify-content:space-between;align-items:center;'>");
            html.append("<div><span style='font-weight:700;font-size:15px;color:#e7e9ea;'>").append(brandingName).append("</span>");
            html.append("<span style='font-size:13px;color:#71767b;margin-left:6px;'>@").append(brandingName.toLowerCase().replaceAll("\\s+", "")).append("</span></div>");
            html.append("<svg width='20' height='20' viewBox='0 0 24 24' fill='#e7e9ea'><path d='M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-4.714-6.231-5.401 6.231H2.748l7.73-8.835L1.254 2.25H8.08l4.253 5.622L18.244 2.25zm-1.161 17.52h1.833L7.084 4.126H5.117z'/></svg>");
            html.append("</div>");
            html.append("<div style='margin-top:6px;font-size:15px;line-height:1.55;color:#e7e9ea;white-space:pre-wrap;'>").append(content).append("</div>");
            html.append("</div></div>");
            // Media
            html.append("<div class='img-box wide' style='margin:0 14px 10px;border-radius:12px;overflow:hidden;'>");
            if (!videoUrl.isBlank()) {
                html.append("<video src='").append(videoUrl).append("' style='position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;' autoplay muted loop playsinline></video>");
            } else if (!imageUrl.isBlank()) {
                html.append("<img src='").append(imageUrl).append("' onerror=\"this.src='https://picsum.photos/1080/608'\">");
            }
            html.append("</div>");
            // Actions
            html.append("<div style='padding:8px 14px 12px;display:flex;gap:24px;color:#71767b;font-size:13px;border-top:1px solid #2f3336;'>");
            html.append("<span style='display:flex;align-items:center;gap:5px;'><svg width='18' height='18' fill='none' stroke='currentColor' stroke-width='1.8' viewBox='0 0 24 24'><path d='M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'/></svg>Répondre</span>");
            html.append("<span style='display:flex;align-items:center;gap:5px;'><svg width='18' height='18' fill='none' stroke='currentColor' stroke-width='1.8' viewBox='0 0 24 24'><polyline points='17 1 21 5 17 9'/><path d='M3 11V9a4 4 0 0 1 4-4h14'/><polyline points='7 23 3 19 7 15'/><path d='M21 13v2a4 4 0 0 1-4 4H3'/></svg>Retweeter</span>");
            html.append("<span style='display:flex;align-items:center;gap:5px;'><svg width='18' height='18' fill='none' stroke='currentColor' stroke-width='1.8' viewBox='0 0 24 24'><path d='M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z'/></svg>J'aime</span>");
            html.append("<span style='display:flex;align-items:center;gap:5px;'><svg width='18' height='18' fill='none' stroke='currentColor' stroke-width='1.8' viewBox='0 0 24 24'><path d='M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8'/><polyline points='16 6 12 2 8 6'/><line x1='12' y1='2' x2='12' y2='15'/></svg>Partager</span>");
            html.append("</div></div></body>");
            }
            default -> {
            String bgColor = platform.equals("LinkedIn") ? "#f3f6f8" : "#f0f2f5";
            String accentColor = platform.equals("LinkedIn") ? "#0077b5" : "#1877f2";
            html.append("<body style='background:").append(bgColor).append(";'><div class='card' style='width:400px;border:1px solid #dde1e5;").append(textAlignStyle).append("'>");
            // Header
            html.append("<div style='padding:10px 12px;display:flex;align-items:center;gap:10px;border-bottom:1px solid #e5e7eb;'>");
            if (!logoUrl.isBlank()) {
                html.append("<img src='").append(logoUrl).append("' style='height:36px;width:auto;object-fit:contain;'>");
                html.append("<p style='font-size:11px;color:#65676b;'>").append(platform).append(" • Sponsorisé</p>");
            } else {
                html.append("<div style='width:36px;height:36px;border-radius:6px;background:").append(accentColor).append(";display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700;font-size:11px;'>").append(platform.substring(0,2)).append("</div>");
                html.append("<div><p style='font-weight:700;font-size:13px;'>").append(brandingName).append("</p><p style='font-size:11px;color:#65676b;'>").append(platform).append(" • Sponsorisé</p></div>");
            }
            html.append("</div>");
            // Text
            html.append("<div style='padding:12px;font-size:13px;color:#050505;line-height:1.5;white-space:pre-wrap;'>").append(content).append("</div>");
            // Media (image ou vidéo)
            html.append("<div class='img-box wide'>");
            if (!videoUrl.isBlank()) {
                html.append("<video src='").append(videoUrl).append("' style='position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;' autoplay muted loop playsinline></video>");
            } else {
                html.append("<img src='").append(imageUrl).append("' onerror=\"this.src='https://picsum.photos/1080/608'\">");
            }
            html.append("</div>");
            // Actions
            html.append("<div style='padding:10px 12px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-around;font-size:13px;font-weight:600;color:#65676b;'><span>J'aime</span><span>Commenter</span><span>Partager</span></div>");
            html.append("</div></body>");
            }
        }

        html.append("</html>");

        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.valueOf("text/html;charset=UTF-8")).body(html.toString());
    }

    /** Historique de générations de l'utilisateur connecté. */
    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Page<ChatHistory> pageResult = chatHistoryRepository
            .findByClientEmailOrderByTimestampDesc(email,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));

        List<Map<String, Object>> items = pageResult.getContent().stream().map(h -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id",        h.getId());
            item.put("prompt",    h.getUserMessage());
            item.put("agentType", h.getAgentType());
            item.put("response",  h.getAiResponse());
            item.put("imageUrl",  h.getImageUrl() != null ? h.getImageUrl() : "");
            item.put("language",  h.getLanguage() != null ? h.getLanguage() : "Français");
            item.put("timestamp", h.getTimestamp() != null ? h.getTimestamp().toString() : "");
            return item;
        }).toList();

        return ResponseEntity.ok(Map.of(
            "items",         items,
            "totalElements", pageResult.getTotalElements(),
            "totalPages",    pageResult.getTotalPages(),
            "currentPage",   page
        ));
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
