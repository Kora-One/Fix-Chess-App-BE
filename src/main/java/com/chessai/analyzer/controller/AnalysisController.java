package com.chessai.analyzer.controller;

import com.chessai.analyzer.service.ChessAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allows your Angular frontend to talk to it
public class AnalysisController {

    @Autowired
    private ChessAnalysisService chessAnalysisService;

    @GetMapping("/analyze/{platform}/{username}/{mood}")
    public String analyze(@PathVariable String platform, @PathVariable String username, @PathVariable String mood) {
        return chessAnalysisService.generateReport(platform, username, mood);
    }
}