package com.chessai.analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChessAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ChessAnalysisService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateReport(String platform, String username, String mood) {
        String pgns;

        if ("lichess".equalsIgnoreCase(platform)) {
            pgns = fetchLichessData(username);
        } else {
            pgns = fetchChessComData(username);
        }

        // Return immediately if the fetchers returned an error message
        if (pgns.startsWith("Error") || pgns.isEmpty()) {
            return pgns;
        }

        return askGemini(username, pgns, mood);
    }

    // --- LICHESS MATCHER (Returns Raw PGNs) ---
    private String fetchLichessData(String username) {
        try {
            // max=20 strictly limits the payload
            String url = "https://lichess.org/api/games/user/" + username + "?max=20";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "chessAI.com - local-development");
            headers.set("Accept", "application/x-chess-pgn"); // Forces Lichess to send raw PGN text

            HttpEntity<?> entity = new HttpEntity<>(headers);
            var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getBody() == null || response.getBody().isBlank()) {
                return "Error: No games found for Lichess user '" + username + "'";
            }
            return response.getBody();

        } catch (Exception e) {
            logger.error("Failed to fetch Lichess data for user: {}", username, e);
            return "Error: Could not find Lichess user '" + username + "'";
        }
    }

    // --- CHESS.COM MATCHER (Parses JSON Archives) ---
    private String fetchChessComData(String username) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "chessAI.com - local-development");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            String archiveUrl = "https://api.chess.com/pub/player/" + username + "/games/archives";
            var archiveResponse = restTemplate.exchange(archiveUrl, HttpMethod.GET, entity, String.class);
            if (archiveResponse.getBody() == null) return "Error: Empty response from chess.com";

            JsonNode archiveBody = objectMapper.readTree(archiveResponse.getBody());
            List<String> archives = new ArrayList<>();
            archiveBody.get("archives").forEach(node -> archives.add(node.asString()));

            if (archives.isEmpty()) return "Error: No games found for this user.";

            String latestMonthUrl = archives.getLast();
            var gamesResponse = restTemplate.exchange(latestMonthUrl, HttpMethod.GET, entity, String.class);
            if (gamesResponse.getBody() == null) return "Error: Empty games response from chess.com";

            JsonNode gamesBody = objectMapper.readTree(gamesResponse.getBody());
            JsonNode games = gamesBody.get("games");
            if (games == null || games.isEmpty()) return "Error: No games found for this month.";

            StringBuilder pgnBuilder = new StringBuilder();
            int total = games.size();
            int limit = Math.min(total, 20); // Strictly limits to top 20 or fewer

            for (int i = total - 1; i >= total - limit; i--) {
                JsonNode pgn = games.get(i).get("pgn");
                if (pgn != null) {
                    pgnBuilder.append(pgn.asString()).append("\n\n");
                }
            }
            return pgnBuilder.toString();

        } catch (Exception e) {
            logger.error("Failed to fetch Chess.com data for user: {}", username, e);
            return "Error: Could not find Chess.com user '" + username + "'";
        }
    }

    // --- GEMINI INTEGRATION ---
    private String askGemini(String username, String pgns, String mood) {
        String requestBody = getString(username, pgns, mood);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(requestBody, headers);

        String fullUrl = geminiApiUrl + "?key=" + geminiApiKey;
        var response = restTemplate.postForEntity(fullUrl, request, String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asString();
        } catch (Exception e) {
            logger.error("Failed to parse Gemini response", e);
            return "Error: Analysis complete, but failed to format the response text.";
        }
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
}