package net.explorviz.code.analysis.service;

/**
 * One analyzed application inside a repository (name and project root path).
 *
 * @param name display name used in landscape / export
 * @param root path relative to repository root; may be empty for repository root
 */
public record ApplicationPath(String name, String root) {
}
