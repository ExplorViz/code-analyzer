package net.explorviz.code.analysis.service;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Local Git repository metadata exposed to the web UI.
 *
 * <p>Registered for reflection because it is returned wrapped in a {@code Response}, which hides
 * the type from Quarkus' native-image build.
 *
 * @param path repository path relative to the local clone root
 * @param branches branch names available in the repository
 */
@RegisterForReflection
public record LocalRepositoryInfo(String path, List<String> branches) {}
