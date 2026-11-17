package net.explorviz.code.analysis.service;

import java.util.List;

/** Lightweight commit metadata collected during a branch walk. */
record CommitWalkEntry(String hash, int commitTime, List<String> parentHashes) {

  CommitWalkEntry {
    parentHashes = List.copyOf(parentHashes);
  }
}
