package net.explorviz.code.analysis.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * In-memory representation of analysis state.
 *
 * <p>Registered for reflection because it is only serialized through a {@code Response} and SSE
 * events, which hides the type from Quarkus' native-image build.
 */
@RegisterForReflection
public record AnalysisProgressState(
    String status,
    int totalCommits,
    int analyzedCommits,
    int totalFiles,
    int analyzedFiles,
    @JsonProperty("currentAnalyzingFile") String currentAnalysingFile) {}
