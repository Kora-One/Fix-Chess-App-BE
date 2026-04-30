package com.chessai.analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChessAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ChessAnalysisService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<String> fetchGamesList(String platform, String username, int limit) {
        if ("lichess".equalsIgnoreCase(platform)) {
            return fetchLichessGamesList(username, limit);
        } else {
            return fetchChessComGamesList(username, limit);
        }
    }

    @Cacheable(value = "ai-reports", key = "#platform + '-' + #username + '-' + #mood + '-' + #limit")
    public String generateReport(String platform, String username, String mood, int limit) {
        List<String> games = fetchGamesList(platform, username, limit);

        if (games.isEmpty()) {
            return "Error: Could not find user or no games found for '" + username + "'";
        }

        String pgns = String.join("\n\n", games);

        String personaInstruction = switch (mood.toLowerCase()) {
            case "roast" -> "Adopt a highly critical, sarcastic, and funny tone. Roast the player ruthlessly for their blunders and missed tactics like an arrogant grandmaster. ";
            case "humor" -> "Keep the tone lighthearted, encouraging, and highly comedic. Use funny analogies to explain the chess moves. ";
            case "sad" -> "Act overly dramatic and mournful about the player's mistakes. Speak as if every blunder is a profound, heart-breaking tragedy. ";
            default -> "Provide a completely professional, objective, and serious analytical chess breakdown. ";
        };

        String prompt = "Act as an expert chess coach. " + personaInstruction + "Analyze the following recent games for the player '" + username + "'. " +
                "You MUST format your response EXACTLY according to the following structure. Keep your tone consistent within these sections:\n\n" +
                "### Introduction\n" +
                "[Overview of their playstyle]\n\n" +
                "### Main Weaknesses\n" +
                "[Bulleted list detailing recurring blunders or inaccuracies]\n\n" +
                "### Advice\n" +
                "[Specific, actionable steps to improve]\n\n" +
                "### Estimated Time to Improve\n" +
                "[Realistic timeframe to see results]\n\n" +
                "Here are the games:\n" + pgns;

        return executeGeminiRequest(prompt);
    }

    private List<String> fetchLichessGamesList(String username, int limit) {
        try {
            String url = "https://lichess.org/api/games/user/" + username + "?max=" + limit + "&opening=true";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "FixChess App - Developer: Ajay Satpati (ajaysatpati9@gmail.com)");
            headers.set("Accept", "application/x-chess-pgn");

            HttpEntity<?> entity = new HttpEntity<>(headers);
            var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();

            if (body == null || body.isBlank()) return new ArrayList<>();

            String[] parts = body.split("(?=\\[Event \")");
            List<String> pgns = new ArrayList<>();
            for (String part : parts) {
                if (!part.trim().isEmpty()) pgns.add(part.trim());
            }
            return pgns;

        } catch (Exception e) {
            logger.error("Failed to fetch Lichess data for user: {}", username, e);
            return new ArrayList<>();
        }
    }

    private List<String> fetchChessComGamesList(String username, int limit) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "FixChess App - Developer: Ajay Satpati (ajaysatpati9@gmail.com)");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            String archiveUrl = "https://api.chess.com/pub/player/" + username + "/games/archives";
            var archiveResponse = restTemplate.exchange(archiveUrl, HttpMethod.GET, entity, String.class);
            if (archiveResponse.getBody() == null) return new ArrayList<>();

            JsonNode archiveBody = objectMapper.readTree(archiveResponse.getBody());
            List<String> archives = new ArrayList<>();
            archiveBody.get("archives").forEach(node -> archives.add(node.asString()));

            if (archives.isEmpty()) return new ArrayList<>();

            String latestMonthUrl = archives.getLast();
            var gamesResponse = restTemplate.exchange(latestMonthUrl, HttpMethod.GET, entity, String.class);
            if (gamesResponse.getBody() == null) return new ArrayList<>();

            JsonNode gamesBody = objectMapper.readTree(gamesResponse.getBody());
            JsonNode games = gamesBody.get("games");
            if (games == null || games.isEmpty()) return new ArrayList<>();

            List<String> pgns = new ArrayList<>();
            int total = games.size();
            int actualLimit = Math.min(total, limit);

            for (int i = total - 1; i >= total - actualLimit; i--) {
                JsonNode pgn = games.get(i).get("pgn");
                if (pgn != null) {
                    pgns.add(pgn.asString());
                }
            }
            return pgns;

        } catch (Exception e) {
            logger.error("Failed to fetch Chess.com data for user: {}", username, e);
            return new ArrayList<>();
        }
    }

    @Cacheable(value = "player-ratings", key = "#platform + '-' + #username")
    public int fetchPlayerRating(String platform, String username) {
        try {
            if ("lichess".equalsIgnoreCase(platform)) {
                return getLichessRating(username);
            } else {
                return getChessComRating(username);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch rating for {}: {}", username, e.getMessage());
            return 1200; // Default fallback rating
        }
    }

    private int getLichessRating(String username) throws Exception {
        String url = "https://lichess.org/api/user/" + username;
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "FixChess App - Developer: Ajay Satpati (ajaysatpati9@gmail.com)");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getBody() == null) return 1200;

        JsonNode perfs = objectMapper.readTree(response.getBody()).path("perfs");
        int max = 0;
        String[] modes = {"rapid", "blitz", "bullet", "classical"};

        for (String mode : modes) {
            int rating = perfs.path(mode).path("rating").asInt(0);
            if (rating > max) max = rating;
        }
        return max > 0 ? max : 1200;
    }

    private int getChessComRating(String username) throws Exception {
        String url = "https://api.chess.com/pub/player/" + username + "/stats";
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "FixChess App - Developer: Ajay Satpati (ajaysatpati9@gmail.com)");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getBody() == null) return 1200;

        JsonNode root = objectMapper.readTree(response.getBody());
        int max = 0;
        String[] modes = {"chess_rapid", "chess_blitz", "chess_bullet"};

        for (String mode : modes) {
            int rating = root.path(mode).path("best").path("rating").asInt(0);
            if (rating > max) max = rating;
        }
        return max > 0 ? max : 1200;
    }

    @Cacheable(value = "player-identities", key = "#username + '-' + #rating + '-' + #mood")
    public Map<String, String> generateCardIdentity(String username, int rating, String mood) {
        String persona = switch (mood.toLowerCase()) {
            case "roast" -> "sarcastic, insulting, and ruthless";
            case "humor" -> "funny, goofy, and silly";
            case "sad" -> "overly dramatic, poetic, and tragic";
            default -> "cool, professional, and intimidating";
        };

        String prompt = "You are a chess persona generator. Generate exactly ONE single animal emoji and ONE short tagline (maximum 8 words) for a chess player named '" + username + "' with a peak rating of " + rating + ". The tone of the tagline MUST be " + persona + ". \n" +
                "Format your response EXACTLY like this: EMOJI|TAGLINE\n" +
                "Do NOT use markdown, do NOT add quotes, and do NOT add any other text.";

        String text = executeGeminiRequest(prompt);

        text = text.replace("`", "").replace("text", "").replace("json", "").trim();

        String[] parts = text.split("\\|");
        if (parts.length == 2) {
            return Map.of(
                    "animal", parts[0].trim(),
                    "tagline", parts[1].replace("\"", "").trim()
            );
        } else {
            logger.error("❌ AI returned a weird format that couldn't be split: {}", text);
            return Map.of("animal", "♟️", "tagline", "A mysterious tactician on the board.");
        }
    }

    @Cacheable(value = "pressure-profiles", key = "#platform + '-' + #username + '-' + #limit")
    public Map<String, Object> generatePressureProfile(String platform, String username, int limit) {
        List<String> games = fetchGamesList(platform, username, limit);

        if (games.isEmpty()) {
            return Map.of("error", "Could not find user or no games found for '" + username + "'");
        }

        String pgns = String.join("\n\n", games);

        String prompt = "Analyze these recent games for the player '" + username + "'. " +
                "Estimate their performance out of 100 when they have plenty of time (Normal) vs time trouble under 30 seconds (Pressure). " +
                "You MUST respond ONLY with a valid JSON object matching this exact structure. Do NOT wrap it in markdown backticks. " +
                "{\n" +
                "  \"attributes\": [\"Opening Prep\", \"Endgame Mastery\", \"Tactics & Patterns\", \"Material Protection\", \"Move Accuracy\"],\n" +
                "  \"normalData\": [85, 70, 80, 75, 82],\n" +
                "  \"pressureData\": [80, 30, 45, 20, 50],\n" +
                "  \"persona\": \"The Bullet Panic-er\",\n" +
                "  \"aiInsight\": \"Your comfort-zone play is solid, but when the clock turns red, you hang pieces recklessly.\"\n" +
                "}\n\n" +
                "Games Data:\n" + pgns;

        String aiJsonText = executeGeminiRequest(prompt);

        try {
            // ⚡ CLEANUP: Strip markdown if Gemini disobeys the "No Markdown" rule
            aiJsonText = aiJsonText.replace("```json", "").replace("```", "").trim();

            // Convert the AI's JSON string into a Java Map
            return objectMapper.readValue(aiJsonText, Map.class);

        } catch (Exception e) {
            logger.error("❌ Failed to parse Pressure Profile JSON: {}", e.getMessage());
            // Fallback Data so the frontend chart doesn't crash
            return Map.of(
                    "attributes", List.of("Opening Prep", "Endgame Mastery", "Tactics", "Protection", "Accuracy"),
                    "normalData", List.of(80, 80, 80, 80, 80),
                    "pressureData", List.of(40, 40, 40, 40, 40),
                    "persona", "The Enigma",
                    "aiInsight", "Data was corrupted by time pressure."
            );
        }
    }

    private String executeGeminiRequest(String prompt) {
        try {
            Map<String, Object> requestMap = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            String requestBody = objectMapper.writeValueAsString(requestMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            String primaryUrl = geminiApiUrl + "?key=" + geminiApiKey;

            String[] fallbackChain = {
                    primaryUrl,
                    primaryUrl.replace("gemini-3.1-flash-lite-preview", "gemini-2.5-flash"),
                    primaryUrl.replace("gemini-3.1-flash-lite-preview", "Gemini 2.5 Flash-Lite")
            };

            for (int i = 0; i < fallbackChain.length; i++) {
                try {
                    var response = restTemplate.postForEntity(fallbackChain[i], request, String.class);
                    return extractTextFromJson(response.getBody());

                } catch (HttpStatusCodeException e) {
                    logger.warn("⚠️ Model attempt {} failed with status {}.", i + 1, e.getStatusCode());

                    if (i == fallbackChain.length - 1) {
                        logger.error("❌ ALL THREE Gemini models failed!");
                        return "Error: Both primary and backup AI models are currently experiencing massive traffic spikes. Please try again in 60 seconds!";
                    }

                    logger.info("🔄 Routing to the next fallback model in the chain...");
                }
            }

        } catch (Exception e) {
            logger.error("Gemini API Request Construction Failure", e);
            return "Error: AI engine failed to process request.";
        }

        return "Error: Unexpected AI failure.";
    }

    private String extractTextFromJson(String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).path("candidates").get(0).path("content").path("parts").get(0).path("text").asString();
    }
}