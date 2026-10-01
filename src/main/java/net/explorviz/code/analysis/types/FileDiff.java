package net.explorviz.code.analysis.types;

import java.util.List;

/**
 * The files that differ between two commits.
 *
 * @param added files that exist only in the new commit, including the new side of every rename
 * @param modified files that exist in both commits with different content
 * @param deleted files that exist only in the old commit, including the old side of every rename
 * @param renamed files that were moved; each entry refers to a file in {@code deleted} and one in
 *     {@code added}
 */
public record FileDiff(
    List<FileDescriptor> added,
    List<FileDescriptor> modified,
    List<FileDescriptor> deleted,
    List<RenamedFile> renamed) {}
