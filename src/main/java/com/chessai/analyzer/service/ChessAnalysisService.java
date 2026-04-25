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

    // ⚡ NEW: Public method to expose games to the frontend graphs
    public List<String> fetchGamesList(String platform, String username, int limit) {
        if ("lichess".equalsIgnoreCase(platform)) {
            return fetchLichessGamesList(username, limit);
        } else {
            return fetchChessComGamesList(username, limit);
        }
    }

    @Cacheable(value = "ai-reports", key = "#platform + '-' + #username + '-' + #mood + '-' + #limit")
    public String generateReport(String platform, String username, String mood, int limit) {
        // ⚡ Reuse the list fetcher for the AI Report!
        List<String> games = fetchGamesList(platform, username, limit);

        if (games.isEmpty()) {
            return "Error: Could not find user or no games found for '" + username + "'";
        }

        // Join the list into a single string for Gemini
        String pgns = String.join("\n\n", games);
        return askGemini(username, pgns, mood);
    }

    // --- LICHESS MATCHER (Returns List of PGNs) ---
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

            // Split the raw text block into individual PGNs
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

    // --- CHESS.COM MATCHER (Returns List of PGNs) ---
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

            // Loop backwards to get most recent games
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

    // --- GEMINI INTEGRATION ---
    private String askGemini(String username, String pgns, String mood) {
        String requestBody = getString(username, pgns, mood);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(requestBody, headers);

        String primaryUrl = geminiApiUrl + "?key=" + geminiApiKey;
        String fallbackUrl = primaryUrl.replace("gemini-3.1-flash-lite-preview", "gemini-3-flash-preview");

        try {
            var response = restTemplate.postForEntity(primaryUrl, request, String.class);
            return parseGeminiResponse(response.getBody());

        } catch (HttpStatusCodeException e) {
            logger.warn("⚠️ Primary model overloaded ({}). Silently routing to fallback model...", e.getStatusCode());
            try {
                var fallbackResponse = restTemplate.postForEntity(fallbackUrl, request, String.class);
                return parseGeminiResponse(fallbackResponse.getBody());
            } catch (HttpStatusCodeException fallbackErr) {
                logger.error("❌ Fallback model also failed: {}", fallbackErr.getStatusCode());
                return "Error: Both primary and backup AI models are currently experiencing massive traffic spikes. Please try again in 60 seconds!";
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } catch (Exception e) {
            logger.error("Failed to parse Gemini response", e);
            return "Error: Analysis complete, but failed to format the response text.";
        }
    }

    private String parseGeminiResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asString();
    }

    private static String getString(String username, String pgns, String mood) {
        String personaInstruction = switch (mood.toLowerCase()) {
            case "roast" -> "Adopt a highly critical, sarcastic, and funny tone. Roast the player ruthlessly for their blunders and missed tactics like an arrogant grandmaster. ";
            case "humor" -> "Keep the tone lighthearted, encouraging, and highly comedic. Use funny analogies to explain the chess moves. ";
            case "sad" -> "Act overly dramatic and mournful about the player's mistakes. Speak as if every blunder is a profound, heart-breaking tragedy. ";
            default -> "Provide a completely professional, objective, and serious analytical chess breakdown. ";
        };

        String prompt = "Act as an expert chess coach. " + personaInstruction + "Analyze the following recent games for the player '" + username + "'. " +
                "You MUST format your response EXACTLY according to the following structure. Keep your tone consistent within these sections:\n\n" +
                "### Introduction\n" +
                "[Overview of their playstyle based on the games]\n\n" +
                "### Main Weaknesses\n" +
                "[Bulleted list detailing recurring blunders or inaccuracies]\n\n" +
                "### Advice\n" +
                "[Specific, actionable steps to improve]\n\n" +
                "### Estimated Time to Improve\n" +
                "[Realistic timeframe to see results]\n\n" +
                "Here are the games:\n" + pgns;

        return "{ \"contents\": [{ \"parts\":[{\"text\": \"" + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\"}] }] }";
    }

    // ⚡ NEW: Fetches the peak rating for the Player Card
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

    // ⚡ UPDATED: Now uses dual-model fallback and strict string cleaning!
    @Cacheable(value = "player-identities", key = "#username + '-' + #rating + '-' + #mood")
    public Map<String, String> generateCardIdentity(String username, int rating, String mood) {
        String persona = switch (mood.toLowerCase()) {
            case "roast" -> "sarcastic, insulting, and ruthless";
            case "humor" -> "funny, goofy, and silly";
            case "sad" -> "overly dramatic, poetic, and tragic";
            default -> "cool, professional, and intimidating";
        };

        // Added strict instructions to avoid markdown
        String prompt = "You are a chess persona generator. Generate exactly ONE single animal emoji and ONE short tagline (maximum 8 words) for a chess player named '" + username + "' with a peak rating of " + rating + ". The tone of the tagline MUST be " + persona + ". \n" +
                "Format your response EXACTLY like this: EMOJI|TAGLINE\n" +
                "Do NOT use markdown, do NOT add quotes, and do NOT add any other text.";

        String requestBody = "{ \"contents\": [{ \"parts\":[{\"text\": \"" + prompt.replace("\"", "\\\"") + "\"}] }] }";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(requestBody, headers);

        String primaryUrl = geminiApiUrl + "?key=" + geminiApiKey;
        String fallbackUrl = primaryUrl.replace("gemini-3.1-flash-lite-preview", "gemini-3-flash-preview");

        try {
            ResponseEntity<String> response;
            try {
                // Attempt 1: Primary Model
                response = restTemplate.postForEntity(primaryUrl, request, String.class);
            } catch (HttpStatusCodeException e) {
                logger.warn("⚠️ Primary model busy for Card Identity. Switching to fallback...");
                // Attempt 2: Fallback Model
                response = restTemplate.postForEntity(fallbackUrl, request, String.class);
            }

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String text = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asString();

            // ⚡ CLEANUP: Remove any accidental markdown backticks or extra newlines the AI might have added
            text = text.replace("`", "").replace("text", "").replace("json", "").trim();

            String[] parts = text.split("\\|");
            if (parts.length == 2) {
                return Map.of(
                        "animal", parts[0].trim(),
                        "tagline", parts[1].replace("\"", "").trim() // Strip accidental quotes
                );
            } else {
                logger.error("❌ AI returned a weird format that couldn't be split: {}", text);
            }
        } catch (Exception e) {
            logger.error("❌ Failed to generate AI identity: {}", e.getMessage());
        }

        // Safe fallback if everything crashes
        return Map.of("animal", "♟️", "tagline", "A mysterious tactician on the board.");
    }
}