package net.explorviz.code.analysis.service;

import java.util.List;

/**
 * Local Git repository metadata exposed to the web UI.
 *
 * @param path     repository path relative to the local clone root
 * @param branches branch names available in the repository
 */
public record LocalRepositoryInfo(String path, List<String> branches) {
}
