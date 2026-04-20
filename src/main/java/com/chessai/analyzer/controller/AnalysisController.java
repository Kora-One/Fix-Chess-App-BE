package com.chessai.analyzer.controller;

import com.chessai.analyzer.service.ChessAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalysisController {

    @Autowired
    private ChessAnalysisService chessAnalysisService;

    // Your existing AI endpoint
    @GetMapping("/analyze/{platform}/{username}/{mood}")
    public String analyze(@PathVariable String platform, @PathVariable String username, @PathVariable String mood) {
        return chessAnalysisService.generateReport(platform, username, mood);
    }

    // ⚡ NEW: Endpoint just for the Angular graphs!
    @GetMapping("/games/{platform}/{username}")
    public ResponseEntity<List<String>> getGames(
            @PathVariable String platform,
            @PathVariable String username,
            @RequestParam(defaultValue = "20") int limit) {

        List<String> games = chessAnalysisService.fetchGamesList(platform, username, limit);

        if (games.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(games);
    }

    // ⚡ NEW: Endpoint just for fetching the Player Card rating
    @GetMapping("/stats/{platform}/{username}")
    public ResponseEntity<Map<String, Integer>> getPlayerStats(
            @PathVariable String platform,
            @PathVariable String username) {

        int rating = chessAnalysisService.fetchPlayerRating(platform, username);

        // Return as a JSON object: {"rating": 1500}
        return ResponseEntity.ok(Map.of("rating", rating));
    }

    // ⚡ NEW: Endpoint for the Angular app to fetch the AI-generated persona
    @GetMapping("/identity/{username}/{rating}/{mood}")
    public ResponseEntity<Map<String, String>> getIdentity(
            @PathVariable String username,
            @PathVariable int rating,
            @PathVariable String mood) {

        return ResponseEntity.ok(chessAnalysisService.generateCardIdentity(username, rating, mood));
    }
}