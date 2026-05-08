package net.explorviz.code.analysis.service;

import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;

import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.graphql.client.core.Argument;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.OperationType;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.explorviz.code.analysis.export.DataExporter;


@ApplicationScoped
public class GithubSocialFetcherService {

  @Inject
  @GraphQLClient("github")
  DynamicGraphQLClient githubClient;

  public void fetchSocialDataInRangeAsync(
      final String repositoryName, Date startDate, Date endDate, DataExporter exporter, String landscapeToken) {
    // TODO:
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    String queryStr = String.format("repo:%s created:%s..%s",
        repositoryName, sdf.format(startDate), sdf.format(endDate));

    boolean hasNextPage = true;
    String cursor = null;
    //
    while (hasNextPage) {
      hasNextPage = false;
      try {

        // TODO: define window to rev walk before?
        Document query = buildSearchQuery(queryStr, cursor);

        var response = githubClient.executeSync(query);

        // Parse and map JSON response

        // Send to exporter, adapt it

        // update cursor

      } catch (Exception e) {

        break;
      }
    }
  }

  /**
   * build search query from given base string.
   *
   * @param searchString base search string with date range
   * @param cursor cursor for pagination, can be null for first page
   * @return GraphQL query document
   */

  private Document buildSearchQuery(final String searchString, String cursor) {
    return document(
        operation(OperationType.QUERY,
            field("search",
                Argument.args(
                    Argument.arg("query", searchString),
                    Argument.arg("type", "ISSUE"),
                    Argument.arg("first", 100),
                    cursor != null ? Argument.arg("after", cursor) : null
                ),
                field("pageInfo",
                    field("hasNextPage"),
                    field("endCursor")
                ),
                field("nodes",
                    field("... on Issue",
                        field("number"),
                        field("title"),
                        field("state"),
                        field("createdAt") // TODO: add more
                    ),
                    field("... on PullRequest",
                        field("number"),
                        field("title"),
                        field("state"),
                        field("createdAt"),
                        field("mergedAt")
                    )
                )
            )
        )
    );
  }

}
