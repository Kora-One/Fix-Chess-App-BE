package com.chessai.analyzer.controller;

import com.chessai.analyzer.service.ChessAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalysisController {

    @Autowired
    private ChessAnalysisService chessAnalysisService;

    // Added the {mood} parameter to the path
    @GetMapping("/analyze/{username}/{mood}")
    public String analyze(@PathVariable String username, @PathVariable String mood) {
        return chessAnalysisService.generateReport(username, mood);
    }
}