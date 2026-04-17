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

    public String generateReport(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "chessAI.com - local-development");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            // Fetch archives
            String archiveUrl = "https://api.chess.com/pub/player/" + username + "/games/archives";
            var archiveResponse = restTemplate.exchange(
                    archiveUrl, HttpMethod.GET, entity, String.class
            );
            if (archiveResponse.getBody() == null) return "Empty response from chess.com";
            JsonNode archiveBody = objectMapper.readTree(archiveResponse.getBody());

            List<String> archives = new ArrayList<>();
            archiveBody.get("archives").forEach(node -> archives.add(node.asString()));

            if (archives.isEmpty()) return "No games found for this user.";

            // Fetch latest month's games
            String latestMonthUrl = archives.getLast();
            var gamesResponse = restTemplate.exchange(
                    latestMonthUrl, HttpMethod.GET, entity, String.class
            );
            if (gamesResponse.getBody() == null) return "Empty games response from chess.com";
            JsonNode gamesBody = objectMapper.readTree(gamesResponse.getBody());

            JsonNode games = gamesBody.get("games");
            if (games == null || games.isEmpty()) return "No games found for this month.";

            // Extract PGNs
            StringBuilder pgnBuilder = new StringBuilder();
            int total = games.size();
            int limit = Math.min(total, 20);
            for (int i = total - 1; i >= total - limit; i--) {
                JsonNode pgn = games.get(i).get("pgn");
                if (pgn != null) {
                    pgnBuilder.append(pgn.asString()).append("\n\n");
                }
            }

            return askGemini(username, pgnBuilder.toString());

        } catch (Exception e) {
            logger.error("Failed to fetch chess data for user: {}", username, e);
            return "Error fetching data: " + e.getMessage();
        }
    }

    private String askGemini(String username, String pgns) {
        // Enforcing a strict template in the prompt
        String requestBody = getString(username, pgns);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(requestBody, headers);

        String fullUrl = geminiApiUrl + "?key=" + geminiApiKey;
        var response = restTemplate.postForEntity(fullUrl, request, String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            return rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asString();

        } catch (Exception e) {
            logger.error("Failed to parse Gemini response for user: {}", username, e);
            return "Analysis complete, but failed to format the response text.";
        }
    }

    private static String getString(String username, String pgns) {
        String prompt = "Act as an expert chess coach. Analyze the following recent games for the player '" + username + "'. " +
                "You MUST format your response EXACTLY according to the following structure. Do not deviate from these headings:\n\n" +
                "### Introduction\n" +
                "[Provide a brief overview of their playstyle and general performance based on the games]\n\n" +
                "### Main Weaknesses\n" +
                "[Provide a bulleted list detailing recurring positional inaccuracies, structural weaknesses, or blunders]\n\n" +
                "### Advice\n" +
                "[Provide specific, actionable steps and training recommendations to improve]\n\n" +
                "### Estimated Time to Improve\n" +
                "[Provide a realistic timeframe to see rating results if this advice is followed consistently]\n\n" +
                "Here are the games:\n" + pgns;

        return "{ \"contents\": [{ \"parts\":[{\"text\": \"" + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\"}] }] }";
    }
}