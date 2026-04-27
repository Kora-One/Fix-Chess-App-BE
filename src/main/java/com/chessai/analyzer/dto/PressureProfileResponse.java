package com.chessai.analyzer.dto;

public record PressureProfileResponse(
        int[] normalData,
        int[] pressureData,
        String persona,
        String aiInsight
) {}