package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FileIdentifier;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.TrackableResourceEvent;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Analyzes a non-linear history and replays the reported commits the way landscape-service does:
 * each commit inherits the files of its first reported parent and applies the reported changes. The
 * result must match the actual git tree of every reported commit.
 *
 * <pre>
 *   R(1000) ── M1(2000) ──────────── MG(4000) ── MG2(5000) ── T(6000)   main
 *     └─ S1(500, clock skew) ── S2(3000) ┘          │
 *                                      U(100) ──────┘                   unrelated root
 *
 * S1 is older than its parent, S2 only changes notes.md, MG deletes b.txt.
 * </pre>
 */
@QuarkusTest
class AnalysisServiceCommitGraphTest {

  @Inject AnalysisService analysisService;

  @TempDir File repoDir;

  private Git git;
  private final Map<String, String> commits = new LinkedHashMap<>();

  @BeforeEach
  void createRepository() throws Exception {
    git = Git.init().setDirectory(repoDir).setInitialBranch("main").call();
    commit("R", 1000, Map.of("a.txt", "a1", "b.txt", "b1", "notes.md", "n1"));
    commit("M1", 2000, Map.of("a.txt", "a2", "b.txt", "b1", "notes.md", "n1"), "R");
    commit("S1", 500, Map.of("a.txt", "a1", "b.txt", "b1", "c.txt", "c1", "notes.md", "n1"), "R");
    commit("S2", 3000, Map.of("a.txt", "a1", "b.txt", "b1", "c.txt", "c1", "notes.md", "n2"), "S1");
    commit("MG", 4000, Map.of("a.txt", "a2", "c.txt", "c1", "notes.md", "n2"), "M1", "S2");
    commit("U", 100, Map.of("u.txt", "u1"));
    commit(
        "MG2",
        5000,
        Map.of("a.txt", "a2", "c.txt", "c1", "notes.md", "n3", "u.txt", "u1"),
        "MG",
        "U");
    commit("T", 6000, Map.of("a.txt", "a3", "c.txt", "c1", "notes.md", "n3", "u.txt", "u1"), "MG2");

    final RefUpdate refUpdate = git.getRepository().updateRef(Constants.R_HEADS + "main");
    refUpdate.setNewObjectId(ObjectId.fromString(commits.get("T")));
    refUpdate.forceUpdate();
    git.reset().setMode(ResetCommand.ResetType.HARD).setRef("main").call();
  }

  @Test
  void walksAllCommitsInTopologicalOrder() throws Exception {
    final List<CommitWalkEntry> entries =
        analysisService.collectCommitWalkEntries(
            git.getRepository(),
            "refs/heads/main",
            Optional.empty(),
            Optional.empty(),
            false,
            false);

    Assertions.assertEquals(Set.copyOf(commits.values()), hashes(entries));
    assertParentsBeforeChildren(entries);
  }

  @Test
  void walksCommitsThatAreNoAncestorsOfTheStartCommit() throws Exception {
    final List<CommitWalkEntry> local =
        analysisService.collectCommitWalkEntries(
            git.getRepository(),
            "refs/heads/main",
            Optional.of(commits.get("M1")),
            Optional.empty(),
            false,
            false);
    Assertions.assertEquals(names("M1", "S1", "S2", "MG", "U", "MG2", "T"), hashes(local));
    Assertions.assertEquals(commits.get("M1"), local.get(0).hash());
    assertParentsBeforeChildren(local);

    final List<CommitWalkEntry> remote =
        analysisService.collectCommitWalkEntries(
            git.getRepository(),
            "refs/heads/main",
            Optional.of(commits.get("M1")),
            Optional.empty(),
            true,
            false);
    Assertions.assertEquals(names("S1", "S2", "MG", "U", "MG2", "T"), hashes(remote));
  }

  @Test
  void walksOnlyAncestorsOfTheEndCommit() throws Exception {
    final List<CommitWalkEntry> entries =
        analysisService.collectCommitWalkEntries(
            git.getRepository(),
            "refs/heads/main",
            Optional.empty(),
            Optional.of(commits.get("S2")),
            false,
            false);

    Assertions.assertEquals(names("R", "S1", "S2"), hashes(entries));
  }

  @Test
  void walksFirstParentCommitsOnly() throws Exception {
    final List<CommitWalkEntry> entries =
        analysisService.collectCommitWalkEntries(
            git.getRepository(),
            "refs/heads/main",
            Optional.empty(),
            Optional.empty(),
            false,
            true);

    Assertions.assertEquals(
        List.of("R", "M1", "MG", "MG2", "T"), entries.stream().map(this::nameOf).toList());
  }

  @Test
  void reportsConsistentStatesForAllCommits() throws Exception {
    final List<CommitData> reports = analyze(config(false), false);

    Assertions.assertEquals(commits.size(), reports.size());
    assertReplayMatchesGit(reports, Set.of(), path -> true);
    Assertions.assertEquals(
        List.of(commits.get("M1"), commits.get("S2")),
        report(reports, "MG").getParentCommitIdsList());
  }

  @Test
  void reportsConsistentStatesForFirstParentCommits() throws Exception {
    final List<CommitData> reports = analyze(config(true), false);

    Assertions.assertEquals(5, reports.size());
    assertReplayMatchesGit(reports, Set.of(), path -> true);
  }

  @Test
  void reportsConsistentStatesWhenResumingAfterPersistedStartCommit() throws Exception {
    final List<CommitData> reports =
        analyze(builder(false).startCommit(Optional.of(commits.get("M1"))).build(), true);

    Assertions.assertEquals(6, reports.size());
    assertReplayMatchesGit(reports, Set.of("R", "M1"), path -> true);
    // S1 forked before the start commit, so it is diffed against its persisted parent.
    Assertions.assertEquals(commits.get("R"), report(reports, "S1").getParentCommitId());
  }

  @Test
  void reportsConsistentStatesWhenStartingLocallyAtStartCommit() throws Exception {
    final List<CommitData> reports =
        analyze(builder(false).startCommit(Optional.of(commits.get("M1"))).build(), false);

    Assertions.assertEquals(7, reports.size());
    assertReplayMatchesGit(reports, Set.of(), path -> true);
  }

  @Test
  void reportsConsistentStatesWithCommitSampling() throws Exception {
    final List<CommitData> reports =
        analyze(builder(false).commitSamplingInterval(Optional.of(2)).build(), false);

    Assertions.assertEquals(4, reports.size());
    assertReplayMatchesGit(reports, Set.of(), path -> true);
  }

  @Test
  void reportsConsistentStatesWhenSkippingIrrelevantCommits() throws Exception {
    final List<CommitData> reports =
        analyze(
            builder(false)
                .skipCommitsWithoutRelevantFileChanges(true)
                .includeInAnalysisExpressions(Optional.of("*.txt"))
                .build(),
            false);

    // S2 only changes notes.md, so the merge MG links to S1, which represents it.
    Assertions.assertEquals(commits.size() - 1, reports.size());
    assertReplayMatchesGit(reports, Set.of(), path -> path.endsWith(".txt"));
    Assertions.assertEquals(
        List.of(commits.get("M1"), commits.get("S1")),
        report(reports, "MG").getParentCommitIdsList());
  }

  @Test
  void reportsConsistentStatesWithCommitLimit() throws Exception {
    final List<CommitData> reports =
        analyze(builder(false).commitAnalysisLimit(Optional.of(3)).build(), true);

    Assertions.assertEquals(3, reports.size());
    assertReplayMatchesGit(reports, Set.of(), path -> true);
  }

  @Test
  void abortsBeforeSendingCommitsIfRemoteStateCannotBeInitialized() {
    final RecordingExporter exporter = new RecordingExporter(true);
    exporter.stateDataFailure = new IllegalStateException("state request failed");

    final RuntimeException exception =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> analysisService.analyzeAndSendRepo(config(false), exporter));

    Assertions.assertSame(exporter.stateDataFailure, exception.getCause());
    Assertions.assertTrue(exporter.commits.isEmpty());
  }

  private List<CommitData> analyze(final AnalysisConfig config, final boolean remote)
      throws Exception {
    final RecordingExporter exporter = new RecordingExporter(remote);
    analysisService.analyzeAndSendRepo(config, exporter);
    return exporter.commits;
  }

  private AnalysisConfig config(final boolean firstParentCommitsOnly) {
    return builder(firstParentCommitsOnly).build();
  }

  private AnalysisConfig.Builder builder(final boolean firstParentCommitsOnly) {
    return new AnalysisConfig.Builder()
        .repoPath(Optional.of(repoDir.getAbsolutePath()))
        .landscapeToken("commit-graph-test")
        .applicationName("commit-graph-test")
        .fetchSocialData(false)
        .syncSocialWindow(false)
        .firstParentCommitsOnly(firstParentCommitsOnly);
  }

  /**
   * Replays the reports like landscape-service: copy the files of the first reported parent, then
   * apply deleted, added and modified files.
   */
  private void assertReplayMatchesGit(
      final List<CommitData> reports,
      final Set<String> persistedBeforehand,
      final Predicate<String> inScope)
      throws Exception {
    final Map<String, Map<String, String>> receiverState = new HashMap<>();
    for (final String name : persistedBeforehand) {
      receiverState.put(commits.get(name), treeOf(commits.get(name), inScope));
    }

    for (final CommitData report : reports) {
      final Map<String, String> state =
          new TreeMap<>(
              receiverState.getOrDefault(report.getParentCommitId(), Collections.emptyMap()));
      report.getDeletedFilesList().forEach(file -> state.remove(file.getFilePath()));
      for (final FileIdentifier file : report.getAddedFilesList()) {
        state.put(file.getFilePath(), file.getFileHash());
      }
      for (final FileIdentifier file : report.getModifiedFilesList()) {
        state.put(file.getFilePath(), file.getFileHash());
      }

      final String name = nameOf(report.getCommitId());
      Assertions.assertEquals(
          treeOf(report.getCommitId(), inScope),
          state,
          "Replayed state of "
              + name
              + " differs from git (parent "
              + report.getParentCommitId()
              + ")");
      Assertions.assertFalse(
          receiverState.containsKey(report.getCommitId()), name + " was reported twice");
      receiverState.put(report.getCommitId(), state);
    }
  }

  private Map<String, String> treeOf(final String commitHash, final Predicate<String> inScope)
      throws Exception {
    final Map<String, String> files = new TreeMap<>();
    final Repository repository = git.getRepository();
    try (RevWalk revWalk = new RevWalk(repository);
        TreeWalk treeWalk = new TreeWalk(repository)) {
      treeWalk.addTree(revWalk.parseCommit(ObjectId.fromString(commitHash)).getTree());
      treeWalk.setRecursive(true);
      while (treeWalk.next()) {
        if (inScope.test(treeWalk.getPathString())) {
          files.put(treeWalk.getPathString(), treeWalk.getObjectId(0).getName());
        }
      }
    }
    return files;
  }

  private void assertParentsBeforeChildren(final List<CommitWalkEntry> entries) {
    final Set<String> seen = new HashSet<>();
    final Set<String> inWalk = new HashSet<>();
    entries.forEach(entry -> inWalk.add(entry.hash()));
    for (final CommitWalkEntry entry : entries) {
      for (final String parent : entry.parentHashes()) {
        if (inWalk.contains(parent)) {
          Assertions.assertTrue(
              seen.contains(parent), nameOf(entry) + " was walked before its parent");
        }
      }
      seen.add(entry.hash());
    }
  }

  private CommitData report(final List<CommitData> reports, final String name) {
    return reports.stream()
        .filter(report -> report.getCommitId().equals(commits.get(name)))
        .findFirst()
        .orElseThrow();
  }

  private Set<String> hashes(final List<CommitWalkEntry> entries) {
    final Set<String> hashes = new HashSet<>();
    entries.forEach(entry -> hashes.add(entry.hash()));
    Assertions.assertEquals(entries.size(), hashes.size(), "Commits were walked twice");
    return hashes;
  }

  private Set<String> names(final String... names) {
    final Set<String> hashes = new HashSet<>();
    for (final String name : names) {
      hashes.add(commits.get(name));
    }
    return hashes;
  }

  private String nameOf(final CommitWalkEntry entry) {
    return nameOf(entry.hash());
  }

  private String nameOf(final String hash) {
    return commits.entrySet().stream()
        .filter(entry -> entry.getValue().equals(hash))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(hash);
  }

  private void commit(
      final String name,
      final int commitTime,
      final Map<String, String> files,
      final String... parents)
      throws Exception {
    final Repository repository = git.getRepository();
    try (ObjectInserter inserter = repository.newObjectInserter()) {
      final TreeFormatter tree = new TreeFormatter();
      for (final Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {
        final ObjectId blobId =
            inserter.insert(Constants.OBJ_BLOB, file.getValue().getBytes(StandardCharsets.UTF_8));
        tree.append(file.getKey(), FileMode.REGULAR_FILE, blobId);
      }

      final PersonIdent ident =
          new PersonIdent(
              "Test",
              "test@example.com",
              java.time.Instant.ofEpochSecond(commitTime),
              java.time.ZoneOffset.UTC);
      final CommitBuilder commitBuilder = new CommitBuilder();
      commitBuilder.setTreeId(inserter.insert(tree));
      commitBuilder.setAuthor(ident);
      commitBuilder.setCommitter(ident);
      commitBuilder.setMessage(name);
      final List<ObjectId> parentIds = new ArrayList<>();
      for (final String parent : parents) {
        parentIds.add(ObjectId.fromString(commits.get(parent)));
      }
      commitBuilder.setParentIds(parentIds);
      final ObjectId commitId = inserter.insert(commitBuilder);
      inserter.flush();
      commits.put(name, commitId.getName());
    }
  }

  private static final class RecordingExporter implements DataExporter {
    private final boolean remote;
    private final List<CommitData> commits = Collections.synchronizedList(new ArrayList<>());

    private RuntimeException stateDataFailure;

    private RecordingExporter(final boolean remote) {
      this.remote = remote;
    }

    @Override
    public StateData getStateData(
        final String repositoryName,
        final String branchName,
        final String token,
        final Map<String, String> applicationPaths,
        final String repositoryUrl,
        final boolean skipLatestCommitLookup) {
      if (stateDataFailure != null) {
        throw stateDataFailure;
      }
      return StateData.newBuilder().build();
    }

    @Override
    public void persistFile(final FileData fileData) {
      // not needed
    }

    @Override
    public void persistCommit(final CommitData commitData) {
      commits.add(commitData);
    }

    @Override
    public void persistTrackableResourceEvent(final TrackableResourceEvent event) {
      // not needed
    }

    @Override
    public boolean isRemote() {
      return remote;
    }

    @Override
    public boolean isInvalidCommitHash(final String hash) {
      return false;
    }
  }
}
