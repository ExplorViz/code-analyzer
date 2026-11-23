# Anonymizing Git-derived analysis data for public upload

Research note, 2026-11-23. Scope: what an "anonymize" option in the code-analyzer form (and the ExplorViz frontend form) must do so that analysis data can be published.

Conventions used below:

- **[F]** = fact, verified in this session against the cited source (quote or code line).
- **[J]** = my judgement / recommendation, not a sourced fact.
- **[U]** = could not be verified from a primary source in this session (see [section 7](#7-verification-gaps)).
- This is engineering research, not legal advice.

## 1. Bottom line

1. **[J]** A single checkbox cannot make data from a **public** repository anonymous in the GDPR sense. With file paths, class names and commit structure intact, the export can be matched back to its origin, and the origin lists every author. For public repositories the option is honestly a *pseudonymisation / data-minimisation* feature. Label and document it that way.
2. **[J]** For **private** repositories the option can get close to anonymous for the public, provided the pseudonymisation key is discarded and the repository identity, commit IDs, blob hashes and GitHub data are removed. The source structure itself (package, class, method names) is then the remaining confidentiality issue, not a personal-data issue.
3. **[J]** The default must cover every direct identifier and every lookup key the analyzer emits today: author name, author email (twice: `ContributorData` and `FileData.last_editor`), GitHub login and avatar URL, issue/PR text and URLs, repository URL and name, commit SHAs, and Git blob hashes. Exact timestamps should be coarsened.
4. **[J]** Do it in one place, inside the analyzer, at the `DataExporter` boundary, so that neither the gRPC nor the JSON output can bypass it.

## 2. What the analyzer extracts and emits

All messages are defined in `src/main/proto/`. Line numbers refer to the current `main` (`66360f7`).

### 2.1 Personal or directly identifying fields

| Field | Where emitted | Source of the value |
|---|---|---|
| `ContributorData.git_username` | `contributor.proto:12` | Git author name, `GitMetricCollector.java:70,75` |
| `ContributorData.email` | `contributor.proto:13` | Git author email, `GitMetricCollector.java:71,76`; GitHub user/commit email, `GithubFetcherService.java:655,806` |
| `ContributorData.github_login` | `contributor.proto:16` | GitHub GraphQL `login`, `GithubFetcherService.java:656,804` |
| `ContributorData.avatar_url` | `contributor.proto:17` | GitHub `avatarUrl`, `GithubFetcherService.java:657,805` |
| `CommitData.author` | `commit.proto:26` | set in `AnalysisService.java:1057-1061` |
| `FileData.last_editor` | `file.proto:99` | **author email in clear text**, `AnalysisService.java:871` → `GitMetricCollector.java:37` → `AbstractFileDataHandler.java:69-70` |
| `TrackableResourceEvent.actor` | `trackable_resource.proto:55` | issue/PR author and per-event actor, `GithubFetcherService.java:651-658,800-807` |
| `TrackableResourceEvent.title`, `.description` | `trackable_resource.proto:59-60` | issue/PR title and full body (free text, may contain names, @mentions, emails), `GithubFetcherService.java:633-634,666-667` |
| `TrackableResourceEvent.web_url` | `trackable_resource.proto:61` | issue/PR URL (contains owner/repo/number), `GithubFetcherService.java:635,668` |

### 2.2 Lookup keys and quasi-identifiers

| Field | Where emitted | Note |
|---|---|---|
| `CommitData.commit_id`, `parent_commit_id`, `parent_commit_ids` | `commit.proto:18,19,38` | real Git SHAs, `CommitReportHandler.java:59-64`, `AnalysisService.java:1018` |
| `TrackableResourceEvent.commit_shas` | `trackable_resource.proto:63` | real SHAs from GitHub, `GithubFetcherService.java:612-621,670` |
| `StateData.commit_id` (returned by backend) | `state.proto:31` | used to resume analysis, `AnalysisService.java:516-527` |
| `FileIdentifier.file_hash`, `FileData.file_hash` | `commit.proto:55`, `file.proto:85` | **Git blob object ID** (content hash), `CommitReportHandler.java:69-71`, `AnalysisService.java:1328,1347` |
| `CommitData.author_date`, `commit_date` | `commit.proto:20-21` | second precision, `AnalysisService.java:1023-1030` |
| `TrackableResourceEvent.event_timestamp` | `trackable_resource.proto:52` | GitHub event time |
| `TrackableResourceEvent.resource_id`, `annotation_id`, `referenced_issue_numbers`, `labels` | `trackable_resource.proto:48,54,62,64` | issue/PR number and GitHub node/event IDs |
| `repository_name` (all messages) | e.g. `commit.proto:17` | last path segment of remote URL or local directory, `AnalysisConfig.java:281-315` |
| `StateDataRequest.repository_url` | `state.proto:21` | clone URL, `AnalysisService.java:1369-1373`, `GrpcExporter.java:79-81`; also written to `StateRequest*.json` by `JsonExporter.java:95-96,122` |
| `CommitData.branch_name`, `StateDataRequest.branch_name` | `commit.proto:22`, `state.proto:14` | `CommitReportHandler.java:66` |
| `CommitData.tags` | `commit.proto:23` | full ref names, `AnalysisService.java:737-749,1052-1053` |
| `StateDataRequest.application_paths` | `state.proto:18` | user-chosen application names and repo-relative roots |
| `file_path` (all file messages) | `commit.proto:58`, `file.proto:88` | repo-relative path |
| `FileData.package_name`, `import_names` | `file.proto:91-92` | |
| Class, field, function, parameter names and types; `annotations`; `enum_values`; `implemented_interfaces`; `superclasses`; `outgoing_method_calls` | `file.proto:41-80` | identifiers from source; annotations are emitted as (qualified) names only, `FileDataVisitor.java:167-177` |
| `start_line`, `end_line`, `metrics`, `added_lines`/`modified_lines`/`deleted_lines`, `analysis_file_count`, `FileRename.similarity` | `file.proto:62-64,96,102-104`, `commit.proto:29,51` | numeric; together they form a structural fingerprint |

### 2.3 What is *not* emitted (verified)

- **[F]** No commit message. `getFullMessage`/`getShortMessage` are never called in `src/main/java`; no proto field carries it. Trailers such as `Signed-off-by` and `Co-authored-by` are therefore not exported.
- **[F]** No committer identity. `getCommitterIdent()` is used only for the date (`AnalysisService.java:1029`).
- **[F]** No time-zone offset. Dates are converted to epoch seconds (`AnalysisService.java:1025,1029`), so the offset in the commit object is dropped.
- **[F]** No source text or comment text in the Java path; comments are only counted (`FileDataVisitor.java:429-441`). **[U]** I did not audit every ANTLR listener for other languages line by line; the proto has no field for source text, so any leak would have to go through a name/type/annotation string.
- **[F]** Clone credentials (`AnalysisRequest.username`/`password`, `index.html:78,88`) are used for cloning and as the GitHub API token (`GithubFetcherService.java:139,167`) and are not part of any proto message.
- **[J]** One edge to check when implementing: `RepositoryFileUrlBuilder.normalizeRepositoryUrl` (`RepositoryFileUrlBuilder.java:12-23`) passes an `https://` URL through unchanged, so a URL with embedded `user:token@` would end up in `StateDataRequest.repository_url`. Strip userinfo regardless of the anonymize option.

Local-only surfaces that are not part of the upload but show the same data: the request log line with the repo path/URL (`AnalysisResource.java:67-71`), the status API field `currentAnalyzingFile` (`AnalysisProgressState.java:19`), and the JSON export directory named after the repository (`JsonExporter.java:44-47`).

## 3. Legal baseline

### 3.1 GDPR definitions

- **[F]** Personal data is "any information relating to an identified or identifiable natural person"; identifiable means "directly or indirectly, in particular by reference to an identifier such as a name, an identification number, location data, an online identifier" ([GDPR Art. 4(1)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)).
- **[F]** Pseudonymisation is processing such that the data "can no longer be attributed to a specific data subject without the use of additional information, provided that such additional information is kept separately" ([GDPR Art. 4(5)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)).
- **[F]** Recital 26: pseudonymised data "which could be attributed to a natural person by the use of additional information should be considered to be information on an identifiable natural person". Identifiability is judged by "all the means reasonably likely to be used, such as singling out, either by the controller or by another person", considering "the costs of and the amount of time required for identification" and available technology. The Regulation does not apply to "personal data rendered anonymous in such a manner that the data subject is not or no longer identifiable" ([GDPR Recital 26](https://eur-lex.europa.eu/eli/reg/2016/679/oj)).
- Text read from the [legislation.gov.uk copy of the regulation as adopted](https://www.legislation.gov.uk/eur/2016/679/adopted), because EUR-Lex refused automated access.

### 3.2 Article 29 WP Opinion 05/2014 (WP216)

Source: [WP216 PDF](https://ec.europa.eu/justice/article-29/documentation/opinion-recommendation/files/2014/wp216_en.pdf). Page numbers are PDF pages.

- **[F]** Three tests for an anonymisation technique: singling out ("the possibility to isolate some or all records which identify an individual in the dataset"), linkability ("the ability to link, at least, two records concerning the same data subject"), and inference (p. 11-12).
- **[F]** "pseudonymisation is not a method of anonymisation. It merely reduces the linkability of a dataset with the original identity of a data subject, and is accordingly a useful security measure" (p. 3). Its summary table rates pseudonymisation and hashing/tokenisation as still open to singling out and linkability (p. 24).
- **[F]** Event-level data stay personal while the original exists: such data "would still qualify as personal data for any party, as long as the data controller (or any other party) still has access to the original raw data, even if direct identifiers have been removed from the set provided to third parties" (p. 9). "removing directly identifying elements in itself is not enough" (p. 9).
- **[F]** Plain hashing: "if the range of input values the hash function are known they can be replayed through the hash function in order to derive the correct value for a particular record"; a salted hash "may still be feasible" to reverse "with reasonable means" (p. 20).
- **[F]** Keyed hash with stored key: the controller can replay it, but for an attacker "the number of possibilities to be tested is sufficiently large as to be impractical" (p. 20). Keyed hash with deletion of the key "may be equated to selecting a random number as a pseudonym for each attribute in the database and then deleting the correspondence table" (p. 21).
- **[F]** Anonymised data "can still present residual risks"; anonymisation "should not be regarded as a one-off exercise" (p. 4).

### 3.3 EDPB Guidelines 01/2025 on Pseudonymisation

Source: [EDPB Guidelines 01/2025, version for public consultation](https://www.edpb.europa.eu/system/files/2025-01/edpb_guidelines_202501_pseudonymisation_en.pdf), adopted 16 January 2025 ([consultation page](https://www.edpb.europa.eu/our-work-tools/documents/public-consultations/2025/guidelines-012025-pseudonymisation_en)).

- **[F]** Status: the document read is marked "Adopted - version for public consultation". **[U]** I found no final version; treat the quotes as draft guidance.
- **[F]** Pseudonymised data "is to be considered information on an identifiable natural person, and is therefore personal. This statement also holds true if pseudonymised data and additional information are not in the hands of the same person. Even if all additional information retained by the pseudonymising controller has been erased, the pseudonymised data can be considered anonymous only if the conditions for anonymity are met" (Executive summary, p. 3).
- **[F]** "Pseudonymisation secrets" are keys or lookup tables; they "form part of the additional information" and must be kept separately. If the controller keeps the original data, "those original data also constitute part of the additional information" (para. 85-86 and footnote 21).
- **[F]** Suitable transformations: "cryptographic one-way functions like Message Authentication Codes (MACs)"; "(keyed) pre-image resistant cryptographic one-way functions (like HMACs)"; for lookup tables "it suffices to choose randomly generated pseudonyms"; secrets "should have sufficient entropy" (para. 88-89, 107).
- **[F]** Quasi-identifiers allow attribution "without the use of the pseudonymisation secrets"; "The most direct way to prevent attribution based on quasi-identifiers is their removal. A second approach lies in their modification by generalisation and randomisation" (para. 101-102).

### 3.4 Case law

- **[F]** CJEU, C-413/23 P, *EDPS v SRB*, 4 September 2025: "pseudonymised data must not be regarded as constituting, in all cases and for every person, personal data"; pseudonymisation "may, depending on the circumstances of the case, effectively prevent persons other than the controller from identifying the data subject" ([CURIA press release 107/25](https://curia.europa.eu/jcms/upload/docs/application/pdf/2025-09/cp250107en.pdf); judgment para. 86).
- **[F]** The same judgment, para. 85 (my translation from the German text): if it cannot be ruled out that third parties are reasonably able to attribute the pseudonymised data to the data subject "by means such as cross-checking with other data available to them", the person is identifiable and the data are personal data, both for the transfer and for later processing by those third parties. German text read from a [university mirror of the judgment](https://www.uni-bamberg.de/fileadmin/datenschutz/Gerichtsentscheidungen/EuGH-Urteil_vom_04.09.2025_-_Rechtssache_C-413-23_P.pdf); official entry: [EUR-Lex C-413/23 P](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:62023CJ0413).
- **[F]** For the controller's own duties, identifiability "must be assessed at the time of collection of the data and from the point of view of the controller" (press release; judgment para. 111).
- **[F]** *Breyer* (C-582/14) as restated in *SRB* para. 83: data that are not personal in themselves relate to an identifiable person when the controller has legal means to obtain the identifying additional information from a third party. **[U]** I could not open the *Breyer* judgment itself ([EUR-Lex C-582/14](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:62014CJ0582)).

### 3.5 What this implies for Git data

- **[J]** Author name and email are personal data (name and online identifier, Art. 4(1)). A GitHub login and an avatar URL are online identifiers too.
- **[J]** A commit SHA of a **public** repository is "additional information" available to everyone: it resolves to the commit object, which carries the author. Under *SRB* para. 85 and Recital 26, an export that keeps such SHAs is personal data for any recipient. The same holds without SHAs if the export can be matched to the public repository by structure.
- **[J]** A consistent pseudonym per contributor keeps singling out and linkability by design (WP216 p. 24). It is pseudonymisation while a key or mapping exists, and it is only "anonymous" if, after discarding the key, nobody can reasonably re-attribute by other means (EDPB summary p. 3).
- **[J]** The person who runs the analyzer keeps the repository, so for that person the export stays personal data (WP216 p. 9; *SRB* para. 111). Publishing is a new disclosure they must be able to justify. The option reduces risk; it does not remove the obligation.

## 4. Git specifics and re-identification

### 4.1 What a commit contains

- **[F]** "A commit encapsulates: all parent object ids; author name, email and date; committer name and email and the commit time" ([git-commit-tree, COMMIT INFORMATION](https://git-scm.com/docs/git-commit-tree#_commit_information)).
- **[F]** The raw object has `author A U Thor <author@example.com> 1465981137 +0000` and a matching `committer` line, i.e. name, email, epoch timestamp and UTC offset; a signature is embedded as "header entry gpgsig" ([gitformat-signature](https://git-scm.com/docs/gitformat-signature)).
- **[F]** Commit messages can carry more identities as trailers, e.g. `Signed-off-by: Alice <alice@example.com>` ([git-interpret-trailers](https://git-scm.com/docs/git-interpret-trailers)); `git commit -s` adds "a Signed-off-by trailer by the committer" ([git-commit](https://git-scm.com/docs/git-commit#Documentation/git-commit.txt--s)).
- **[F]** A `.mailmap` file in the tree maps "author and committer names and email addresses to canonical real names and email addresses" ([gitmailmap](https://git-scm.com/docs/gitmailmap)). **[J]** It is a file path the analyzer reports, but its content is not exported.
- **[F]** GitHub `noreply` addresses still identify the account: the format is "an ID number and your username", `ID+USERNAME@users.noreply.github.com`, or `USERNAME@users.noreply.github.com` for older accounts ([GitHub email addresses reference](https://docs.github.com/en/account-and-profile/reference/email-addresses-reference)).
- Git facts read from the local manual pages of Git 2.56.1; the `git-fast-export` text was also compared with git-scm.com.

### 4.2 Why IDs and metadata re-identify

- **[F]** GitHub commit search: "The `hash` qualifier matches commits with the specified SHA-1 hash"; `author-date`/`committer-date` match by date; `author-email` matches "the author's or committer's full email address" ([GitHub: Searching commits](https://docs.github.com/en/search-github/searching-on-github/searching-commits)). A SHA is therefore a direct lookup key into public repositories.
- **[F]** Software Heritage indexes file contents by "the Git SHA-1 of the content" ([swh-dataset relational schema](https://github.com/SoftwareHeritage/swh-dataset/blob/master/docs/graph/schema.rst)). **[J]** The analyzer's `file_hash` is exactly that value, so each blob hash of a public file is also a lookup key.
- **[F]** Hashing identifiers from a guessable input space is reversible by replay (WP216 p. 20). The AEPD/EDPS paper adds a concrete email case: "users' email addresses can be derived through their hash value" in the Gravatar service, and "hash functions are generally not recommended for pseudonymisation of personal data" (quoting ENISA) ([AEPD/EDPS, Introduction to the hash function as a personal data pseudonymisation technique, 2019](https://www.aepd.es/guides/introduction-to-hash-function-as-personal-data-pseudonymisation-technique.pdf), p. 30). Its conclusions require "a high level of information entropy" and "single-use salt/random values" (p. 23).
- **[J]** Emails of a known project are a tiny dictionary (the contributor list is public), so `sha256(email)` is not protection. Use a keyed function with a secret random key, or random labels.
- **[F]** Sparse event data re-identify with little side knowledge: "an adversary who knows only a little bit about an individual subscriber can easily identify this subscriber's record in the dataset" ([Narayanan and Shmatikov, arXiv:cs/0610105](https://arxiv.org/abs/cs/0610105)). **[J]** By analogy, a sequence of exact commit timestamps is a fingerprint of a repository and of a person's activity.
- **[F]** Code itself identifies authors: stylometry attributed 1,600 and 250 authors "with significantly higher accuracy (94% and 98%)" ([Caliskan-Islam et al., USENIX Security 2015](https://www.usenix.org/conference/usenixsecurity15/technical-sessions/presentation/caliskan-islam)), and short fragments reached "approximately 50% or 60%" for 104 programmers ([Dauber et al., PoPETs 2019(3)](https://petsymposium.org/popets/2019/popets-2019-0053.php)). **[J]** Limited relevance here because the analyzer exports identifiers and metrics, not source text.
- **[F]** MSR ethics literature: "Anonymisation of such data is almost impossible to achieve as re-identification of the data is almost always possible through the code changes themselves (Yamashita et al. 2017). However, anonymisation and pseudonymisation should still be used to lower the risk" ([Gold and Krinke, Empirical Software Engineering 27:17, 2022](https://doi.org/10.1007/s10664-021-10057-7), p. 21; read from the [UCL open-access copy](https://discovery.ucl.ac.uk/id/eprint/10137505/)).

## 5. Existing practice

| Project / tool | What it does | Source |
|---|---|---|
| Software Heritage graph dataset | **[F]** "some columns containing personal information are pseudonimized in the dataset using a hash algorithm"; revision `author`/`committer` are "an anonymized hash"; the `person` table (`fullname`, `sha256_fullname`) "is not publicly available". Revision IDs (commit hashes), messages, dates and time-zone offsets are published unmodified. | [swh-dataset schema.rst](https://github.com/SoftwareHeritage/swh-dataset/blob/master/docs/graph/schema.rst) |
| GHTorrent | **[F]** After a 2016 complaint about researchers harvesting emails: "No real names or emails will be shared in the MySQL dumps"; a separate `login,email,name` file only on stated terms; opt-out replaces fields "with random strings". Gold and Krinke confirm emails were removed from the dump in March 2016. | [Gousios, "Issue 32", 2016](https://gousios.org/blog/Issue-thirty-two.html); [Gold and Krinke](https://doi.org/10.1007/s10664-021-10057-7), p. 2 |
| CROP code-review dataset | **[F]** "All names in the collected metadata and the code review discussions were replaced by randomly generated pseudonyms"; but "As long as the actual code review is still publicly available, it would be possible to reconstruct the original name and email address", and the metadata "still contains the commit id of the original commit". | [Gold and Krinke](https://doi.org/10.1007/s10664-021-10057-7), p. 28 |
| World of Code | **[F]** Authors are keyed by "a combination of name and email"; access is by registered account. **[U]** I found no privacy or anonymisation statement. | [woc-hack/tutorial README](https://github.com/woc-hack/tutorial) |
| `git fast-export --anonymize` | **[F]** Replaces "all refnames, paths, blob contents, commit and tag messages, names, and email addresses"; equal strings map equally ("two commits with the same author will have the same anonymized author"); it **retains** "the relationship between commits, branches, and tags ... as well as the commit timestamps" and "the relative makeup of the tree". Purpose: sharing a private repo to reproduce Git bugs. `--anonymize-map=<from>[:<to>]` exempts or maps tokens; paths are split "into tokens at slash boundaries". | [git-fast-export, ANONYMIZING](https://git-scm.com/docs/git-fast-export#_anonymizing) |
| git-filter-repo | **[F]** Building blocks only: `--mailmap`, `--replace-text`, `--replace-message`, `--name-callback`, `--email-callback`, `--message-callback`; rewriting gives commits "new hashes". No built-in anonymize mode (the manual does not contain the word). | [git-filter-repo manual](https://github.com/newren/git-filter-repo/blob/main/Documentation/git-filter-repo.txt) |
| ACM SIGSOFT Empirical Standard, Repository Mining | **[F]** Desirable: "discusses ethical issues in mining of software repositories (e.g., data privacy)"; "obfuscating some details of the dataset due to ethical concerns" is an acceptable deviation. | [RepositoryMining.md](https://github.com/acmsigsoft/EmpiricalStandards/blob/master/docs/standards/RepositoryMining.md) |
| MSR Data Showcase practice | **[F]** Only 14 of 112 Data Showcase papers (2014-2021) discussed ethics issues. | [Gold and Krinke](https://doi.org/10.1007/s10664-021-10057-7), p. 5-6 |

**[J]** Takeaways. Software Heritage hashes identities but keeps commit IDs, so its dataset is re-linkable by design and it relies on access terms, not anonymity. `git fast-export --anonymize` is the closest model for a "strict" level: consistent relabelling of everything nameable while keeping shape and timestamps. No surveyed project claims that structure-preserving repository data is anonymous.

## 6. Recommendations

Everything in this section is **[J]** unless marked.

### 6.1 Techniques and trade-offs

| Technique | Use for | Trade-off |
|---|---|---|
| Suppression (empty field) | `repository_url`, `avatar_url`, `github_login`, `web_url`, issue/PR `title` and `description` | Loses links back to the forge and all issue text. |
| Consistent pseudonym | contributors, commit IDs, blob hashes | Keeps "who changed what together" and the commit graph; keeps singling out and linkability. |
| Sequential label ("Contributor 1") | display name | Readable; needs a mapping table that survives incremental runs; numbering by first appearance reveals join order. |
| Keyed HMAC-SHA-256 with a random per-landscape key | contributor ID, commit ID, blob hash | Stateless and consistent across runs while the key exists; reversible by replay for whoever holds the key (WP216 p. 20). Never plain or publicly salted hashes. |
| Generalisation | timestamps to UTC day | Breaks exact-time matching; intraday ordering must come from parent links. |
| Relabelling | branch, tags, paths, identifiers | Paths and identifiers are the visualisation's labels; relabelling them makes the city anonymous but unreadable. |

Mapping table or key:

- **Kept** (in the analyzer's local storage, never sent): pseudonyms stay stable across incremental runs and the owner can reverse them. This is pseudonymisation; the key is "additional information" (EDPB para. 86).
- **Discarded** after the run: closer to anonymisation for private repositories, but a later incremental run cannot continue the same landscape. Resuming needs the key, because the backend returns the last persisted `commit_id` (`AnalysisService.java:516-527`) and the analyzer must map it back to a real commit.
- Recommendation: generate a random 256-bit key per landscape token, keep it in memory by default and discard it at the end of the run; offer "keep key for incremental analysis" as an explicit sub-option.

### 6.2 Field-by-field treatment

| Field | Risk | Default "anonymize" | Strict level | Utility lost |
|---|---|---|---|---|
| `git_username`, `email` | Direct identifier | Replace both with one pseudonym per normalised email (`Contributor <6 hex>`); email empty | same | Real names in author views. A person using several emails becomes several contributors unless `.mailmap` is applied first. |
| `FileData.last_editor` | Direct identifier (email) | Same pseudonym as above | same | None beyond the name. |
| `github_login`, `avatar_url`, `contributor_id` | Direct identifier; avatar URL embeds the account | Suppress `avatar_url`; pseudonymise login with the same key | same | Avatars; Git author and GitHub actor are no longer shown as one person. |
| Issue/PR `title`, `description`, `web_url`, `labels`, `resource_id`, `annotation_id` | Free text with names; URL and numbers resolve directly to the public issue | **Do not fetch**: force `fetchSocialData=false` while anonymize is on | same | Whole issue/PR layer. A reduced variant (events and states with random IDs, no text, no URL) is possible later. |
| `commit_id`, `parent_commit_id(s)`, `commit_shas` | Lookup key into the origin | HMAC to 40 hex chars; apply the same mapping to parents | same | Cannot open a commit on the forge or check it out by ID. Graph, order and merges are preserved. |
| `file_hash` | Content hash; lookup key for public blobs | HMAC, same key | same | None inside ExplorViz (equality is preserved). |
| `author_date`, `commit_date`, `event_timestamp` | Quasi-identifier; activity profile | Truncate to 00:00 UTC of the day | Replace with day offset from the first analysed commit | Time-of-day analysis; in strict mode also the calendar date. Time zone is already not exported [F]. |
| `repository_url` | Names the origin | Suppress | same | "Open in repository" links. |
| `repository_name`, export directory name | Names the origin | Replace with a user-supplied alias, default `repository-<4 hex>` | same | Recognisable project name. |
| `application_paths` (names and roots) | User-chosen names; roots are paths | Keep (the user typed them); warn in the form | Relabel like paths | Strict: application names. |
| `branch_name` | May contain user names or ticket IDs | Keep if `main`, `master`, `develop` or `trunk`, otherwise `branch-1` | Always generic | Branch label. |
| `tags` | Help identify the project and release | Keep | Relabel `tag-N` | Strict: release labels. |
| `file_path`, `package_name`, `import_names`, class/field/function/parameter names and types, `annotations`, `enum_values`, `outgoing_method_calls`, supertypes | Identify a public project by search; for private code this is the confidential part; may contain personal names (`/home/jdoe`, `de.jdoe.util`) | **Keep** | Consistent per-token relabelling (split paths at `/` and qualified names at `.`, as `git fast-export` does), keep file extensions and well-known library prefixes | Strict: the visualisation loses all meaningful labels; structure, sizes, coupling and evolution stay. |
| Metrics, line numbers, change counts, rename similarity | Structural fingerprint only | Keep | Keep | None. |
| `landscape_token` | Access handle, not personal | Keep | Keep | None. |

### 6.3 Levels and UI

1. **Off** (today's behaviour).
2. **Anonymize contributors and origin** (the single checkbox, default treatment in the table): removes every direct identifier and every lookup key; keeps the code structure readable.
3. **Strict: also obfuscate code structure** (optional second control, only useful for private code): adds identifier/path relabelling, tag relabelling and relative dates.

Form behaviour:

- Add `anonymize` (boolean) and `anonymizationLevel` to `AnalysisRequest` (`AnalysisRequest.java:35-62`) and to the form next to `sendToRemote` (`index.html:387-398`).
- When checked: disable and uncheck `fetchSocialData` (`index.html:398`), show a repository-alias field, and show the residual-risk text from 6.5.
- Label suggestion: "Anonymize contributors and repository origin". Avoid a bare "anonymous" promise in the help text for public repositories.

### 6.4 Where in the pipeline

- Put one `AnonymizingExporter` decorator around the `DataExporter` implementations (`GrpcExporter`, `JsonExporter`). Every outgoing message type already passes through that interface: commits, files, state requests, and resource events. Transforming at this boundary covers both the gRPC and the JSON output and keeps the analysis code unchanged.
- It must run inside the analyzer process. The backend, the frontend and any JSON file on disk should only ever see transformed data; the key never leaves the analyzer.
- The decorator owns the reverse lookup needed for resuming: map the pseudonymised `StateData.commit_id` back by recomputing the HMAC over the branch's commits.
- Fail closed: if anonymize is on and a message type has no transformer, do not send it. This protects against new proto fields being added later without a treatment.
- The frontend form should only pass the flag through. **[U]** I could not inspect the frontend or backend (not in this repository): check that neither stores the repository URL, real commit IDs or contributor data from another path, and that an existing landscape is not mixed from anonymized and non-anonymized runs.
- Add a test that runs an analysis with the option on and asserts that no original author name, email, SHA, blob hash, URL or repository name occurs anywhere in the serialized output.

### 6.5 Residual risks to state explicitly

1. **Public repositories remain matchable.** With paths and class names intact (default level), a code search finds the project in seconds; the original history then reveals each contributor behind a pseudonym by aligning commits. Even at the strict level, the shape (files per commit, sizes, graph, dates) is a fingerprint, as the `git fast-export` documentation's own description of what is retained shows.
2. **Small teams.** In a project with two or three known developers, "Contributor a3f9c1" is identifiable from context. Pseudonyms do not prevent singling out (WP216 p. 24).
3. **Names inside code.** Personal names or user names in package names, paths, identifiers or application names pass through at the default level.
4. **The uploader still holds the original.** For them the export is personal data, and publishing it is their processing decision (WP216 p. 9; *SRB* para. 111).
5. **Key handling.** If the key is kept, anyone who obtains it can confirm a guessed email or SHA by replay.
6. **Private code.** At the default level the code structure of a private repository is published in full. That is a confidentiality decision, independent of personal data.
7. **Already-uploaded data.** Turning the option on later does not anonymize a landscape that was uploaded before.

### 6.6 Priorities

1. Contributor name/email, `last_editor`, GitHub login/avatar; disable social data. (Highest: direct identifiers.)
2. Commit IDs, blob hashes, repository URL and name. (Lookup keys.)
3. Timestamp coarsening and branch name.
4. Fail-closed exporter, leak test, form text.
5. Strict level with identifier/path relabelling. (Largest effort, largest utility loss; only for private code.)

## 7. Verification gaps

- EUR-Lex refused automated access. GDPR text was read from legislation.gov.uk; *Breyer* (C-582/14) was not read directly, only as restated in *SRB* para. 83.
- *SRB* judgment paragraphs were read in German from a university mirror and translated by me; the English wording comes from the CURIA press release, which is "not binding on the Court".
- EDPB Guidelines 01/2025: only the consultation version was found. Whether a final version exists, and how the EDPB adjusts it after *SRB*, is unverified.
- Software Heritage: the documentation site and legal pages blocked automated access. The schema quote comes from the project's GitHub mirror of `swh-dataset`. The exact hash construction of the `author` column and Software Heritage's privacy and bulk-access policies are unverified.
- Yamashita et al. 2017 (rewriting Git history to remove personal data) is cited only via Gold and Krinke.
- The published Narayanan and Shmatikov S&P 2008 paper was not reachable; the quote is from the arXiv preprint and says nothing specific about timestamps.
- World of Code and MSR track calls were only partially readable; no anonymisation requirement was found in what was read.
- ANTLR listeners for non-Java languages were not audited line by line for string content.
- The ExplorViz frontend and backend were not inspected.

## 8. Open questions for the team

1. Should pseudonyms be stable across incremental runs (key kept locally) or is a one-shot export enough (key discarded)?
2. Is the main use case public open-source repositories (then: pseudonymisation, readable structure) or private/industrial code (then: the strict level matters)?
3. Does the frontend need the repository URL for any feature that would break when it is empty?
4. Should a reduced, text-free issue/PR layer be supported under anonymize, or is "off" acceptable?
5. Who is the controller for published landscapes, and does the upload flow need a notice telling uploaders that the data stay personal data for public repositories?
