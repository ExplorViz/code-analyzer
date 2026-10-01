package net.explorviz.code.analysis.types;

/**
 * A file that git detected as moved between two commits.
 *
 * <p>Both sides are also part of the regular change lists of the surrounding {@link FileDiff}: the
 * old file is reported as deleted and the new file as added. This keeps consumers that are unaware
 * of renames working and makes the rename purely additional information.
 *
 * @param oldFile the file at its path in the old commit
 * @param newFile the file at its path in the new commit
 * @param similarity content similarity in percent, 100 for a pure move without content changes
 */
public record RenamedFile(FileDescriptor oldFile, FileDescriptor newFile, int similarity) {}
