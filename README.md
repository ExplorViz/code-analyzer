# code-analyzer

This project is a tool that analyzes a Git repository containing a java project.

## Usage and Run Modes

The property `explorviz.gitanalysis.run-mode` determines if the code-analyzer runs continuously with REST APIs or in a non-interactive mode for CI environments.

### Launching The Web Interface for Development

1. Start the Quarkus dev server:
    ```bash
    ./gradlew quarkusDev
    ```
2. Visit [http://localhost:8078/](http://localhost:8078/). The new single-page UI is served directly from `src/main/resources/META-INF/resources/index.html`.
3. Configure one or more **applications** (name and optional repo-relative root for each), or any optional parameters such as branch, filters, credentials, metrics toggles, etc.
4. Hit **Run Analysis**. The page calls the REST endpoint at `/api/analysis/trigger` and streams the textual response back into the UI.

The form mirrors the fields of `AnalysisRequest`, so anything you can configure via JSON can now be triggered from the browser. For several apps in one repo (monorepo), send an `applications` array, for example:

```json
"applications": [
  { "name": "service-a", "root": "apps/service-a" },
  { "name": "service-b", "root": "apps/service-b" }
]
```

Legacy fields `applicationName` and `applicationRoot` still work when `applications` is omitted.

### CI / Non-Interactive Mode

By changing some settings in the `application.properties` file, it is possible to run the code-analyzer as CI job.

| property name                           | property value           |
| --------------------------------------- | ------------------------ |
| explorviz.gitanalysis.remote.url        | ${CI_REPOSITORY_URL:}    |
| explorviz.gitanalysis.branch            | ${CI_COMMIT_BRANCH:}     |
| explorviz.gitanalysis.start-commit-sha1 | ${CI_COMMIT_BEFORE_SHA:} |
| explorviz.gitanalysis.end-commit-sha1   | ${CI_COMMIT_SHA:}        |

Setting the `explorviz.gitanalysis.remote.url` property is mandatory.
The `explorviz.gitanalysis.start-commit-sha1` can be helpful if no fetching from a remote is done, as the analysis will only be performed on subsequent commits.
The `explorviz.gitanalysis.end-commit-sha1` should not be necessary at all.
The CI-Pipeline normally runs on the latest commit so the value of the property is always the latest commit's sha1 value, therefore leaving the property empty produces the same outcome.

## Settings

As the code-analyzer is made to run against your specific repository, you have to change some settings before it is able to analyze it.
All settings can be manipulated via the `application.properties` file.

### Selecting the Repository Source

The repository source get selected automatically based on the settings. Decisive are the values of
the [local storage path](#explorvizgitanalysislocalstorage-path),
the [remote storage path](#explorvizgitanalysisremotestorage-path), the [remote url](#explorvizgitanalysisremoteurl) and
the [branch](#explorvizgitanalysisbranch). Source is selected as follows:

| local.storage-path | remote.storage-path | remote.url   | branch       | repository source                                          |
| ------------------ | ------------------- | ------------ | ------------ | ---------------------------------------------------------- |
| set                | set or empty        | set or empty | empty        | locally available repository, use current branch           |
| set                | set or empty        | set or empty | set          | locally available repository, checkout given branch        |
| empty              | set                 | set          | empty        | cloning repository to path, checkout default branch        |
| empty              | set                 | set          | set          | cloning repository to the path, checkout given branch      |
| empty              | empty               | set          | empty        | cloning repository to temp folder, checkout default branch |
| empty              | empty               | set          | empty        | cloning repository to temp folder, checkout given branch   |
| empty              | set or empty        | empty        | set or empty | INVALID                                                    |

To see the restrictions of the different settings values, consider its respective description.

### explorviz.gitanalysis.local.storage-path

Type: String or empty

This is the path to an already cloned local repository. Absolute paths are supported. Relative paths are resolved from the configured `explorviz.gitanalysis.remote.storage-path`, which defaults to `cloned-repositories`.

### explorviz.gitanalysis.remote.storage-path

Type: String or empty

This is the path to the storage folder for the repository.
It can either be an absolute path, a relative path (considering the execution folder as root) or omitted.
If it is omitted, a folder called "TemporaryRepository" gets created in the temp folder of your machine.
Any successive execution will create a new temp folder and will clone the repository again.

### explorviz.gitanalysis.remote.url

Type: String or empty

This is the remote url to the git repository.
Only HTTP and HTTPS cloning is supported.
If an SSH url is provided, it gets converted to an HTTP url, if possible.

### explorviz.gitanalysis.branch

Type: String or empty

This is the branch name to be analyzed.
If omitted the current ord default branch is used.
The analysis always considers all commits within the branch, starting with the first commit in the repository.
If you only want to analyze the commits since the creation of the branch, use the [start commit sha](#explorvizgitanalysisstart-commit-sha1) setting to start the analysis with the first commit of the branch.

### explorviz.gitanalysis.remote.username

Type: String or empty

The username used to access and clone private repositories.
Leave blank if you clone from a public repository.

### explorviz.gitanalysis.remote.password

Type: String or empty

The password used to access and clone private repositories.
Leave blank if you clone from a public repository.

### explorviz.gitanalysis.fetch-remote-data

Type: Boolean or Empty (defaults to false)

If a remote storage is used and this is set to true, the analysis acts "state aware", fetching the latest state of the analysis data for the given branch from the remote.
The analysis begins with the first new commit and proceeds to the latest available.
[Start commit](#explorvizgitanalysisstart-commit-sha1) and [end commit](#explorvizgitanalysisend-commit-sha1) settings are ignored.

If set to false, the analysis ignores any remote state but acts according to the restrictions given by the [start commit](#explorvizgitanalysisstart-commit-sha1) and [end commit](#explorvizgitanalysisend-commit-sha1) settings.

### explorviz.gitanalysis.send-to-remote

Type: Boolean or Empty (defaults to false)

If a remote storage is used and this is set to true, the analysis data will be sent to the remote endpoint, if set to false, the analysis data will be stored as json on disc.
The storage location will be printed on startup and is relative to the java working directory.

### explorviz.gitanalysis.include-in-analysis-expressions

Type: String or empty

Only files contained in the folders matching one of the search expressions are analyzed.
Provide one or multiple search expressions.

### explorviz.gitanalysis.exclude-from-analysis-expressions

Type: String or empty

Only files with a filename matching one of the search expressions are analyzed.
Provide one or multiple search expressions.

### explorviz.gitanalysis.start-commit-sha1

Type: String or empty

The full SHA-1 hash of a commit used to define the starting point of the analysis.
The Commit must be reachable in the given [branch](#explorvizgitanalysisbranch).
The analysis includes the given commit.
If [fetching from the remote](#explorvizgitanalysisfetch-remote-data) is enabled, this setting is ignored.

### explorviz.gitanalysis.end-commit-sha1

Type: String or empty

The full SHA-1 hash of a commit used to define the end point of the analysis.
The Commit must be reachable in the given [branch](#explorvizgitanalysisbranch).
This commit is included in the analysis, the analysis ends with the analysis of this commit.
If [fetching from the remote](#explorvizgitanalysisfetch-remote-data) is enabled, this setting is ignored.

### explorviz.gitanalysis.calculate-metrics

Type: Boolean or Empty (defaults to false)

Enables the calculation of metrics that are added to the analysis data.

### explorviz.gitanalysis.assume-unresolved-types-from-wildcard-imports

Type: Boolean or Empty (defaults to false)

If wildcard imports are used in the java files, it is not possible to determine some types.
If only a single wildcard import in a file is detected, setting this setting to true results in "assuming the unresolvable types are defined in the wildcard import".
If disabled, no fully qualified name will be provided for these types.

If more than one wildcard import is found, this setting automatically is disabled for the file in question.

### Search Expressions

Search expressions are simple strings to define paths relative to the repository path.
Multiple expressions can be defined by simply comma-separating them.

#### Wildcard

If the folder location changes over the course of the development, it is possible to use the wildcard character \* (asterisk).

Leading Wildcard:

E.g. the folder location `**/src/main/java/**` is searched in the repository everywhere, until some folder hierarchy matches `src/main/java` for example the path `project/javasources/src/main/java`.
The first match is used, if there are multiple matches, try to specify the path even more.
Keep in mind to not use a line separator in front of or after the wildcard.

Infix Wildcards:

Multiple consecutive wildcards e.g. `/some/*/*/path` enforce a certain depth of the directories.
