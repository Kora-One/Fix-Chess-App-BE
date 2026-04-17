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

    @GetMapping("/analyze/{username}")
    public String analyze(@PathVariable String username) {
        return chessAnalysisService.generateReport(username);
    }
}