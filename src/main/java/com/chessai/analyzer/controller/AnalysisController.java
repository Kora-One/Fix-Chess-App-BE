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

    @GetMapping("/analyze/{platform}/{username}/{mood}")
    public String analyze(@PathVariable String platform, @PathVariable String username, @PathVariable String mood, @RequestParam(defaultValue = "20") int limit) {
        return chessAnalysisService.generateReport(platform, username, mood, limit);
    }

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

    @GetMapping("/stats/{platform}/{username}")
    public ResponseEntity<Map<String, Integer>> getPlayerStats(
            @PathVariable String platform,
            @PathVariable String username) {

        int rating = chessAnalysisService.fetchPlayerRating(platform, username);
        return ResponseEntity.ok(Map.of("rating", rating));
    }

    @GetMapping("/identity/{username}/{rating}/{mood}")
    public ResponseEntity<Map<String, String>> getIdentity(
            @PathVariable String username,
            @PathVariable int rating,
            @PathVariable String mood) {

        return ResponseEntity.ok(chessAnalysisService.generateCardIdentity(username, rating, mood));
    }

    @GetMapping("/pressure/{platform}/{username}")
    public ResponseEntity<Map<String, Object>> getPressureProfile(
            @PathVariable String platform,
            @PathVariable String username,
            @RequestParam(defaultValue = "20") int limit) {

        Map<String, Object> profile = chessAnalysisService.generatePressureProfile(platform, username, limit);

        if (profile.containsKey("error")) {
            return ResponseEntity.badRequest().body(profile);
        }
        return ResponseEntity.ok(profile);
    }
}