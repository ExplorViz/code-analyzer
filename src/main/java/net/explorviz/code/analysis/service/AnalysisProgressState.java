package net.explorviz.code.analysis.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * In-memory representation of analysis state.
 */
public record AnalysisProgressState(
    String status,
    int totalCommits,
    int analyzedCommits,
    int totalFiles,
    int analyzedFiles,
    @JsonProperty("currentAnalyzingFile") String currentAnalysingFile) {
}
