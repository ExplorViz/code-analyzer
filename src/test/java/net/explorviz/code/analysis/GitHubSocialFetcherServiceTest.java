package net.explorviz.code.analysis;

import static org.mockito.Mockito.mock;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Calendar;
import java.util.Date;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.service.GithubCollaborationFetcherService;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class GitHubSocialFetcherServiceTest {

  @Inject
  GithubCollaborationFetcherService githubSocialFetcherService;

  @Test
  void testFetchSocialDataInRange() {
    System.out.println("Starting testFetchSocialDataInRange");
    // Range: 2023-01-01 to 2023-02-01
    Calendar cal = Calendar.getInstance();
    cal.set(2026, Calendar.APRIL, 1);
    Date startDate = cal.getTime();
    cal.set(2026, Calendar.MAY, 9);
    Date endDate = cal.getTime();

    String repositoryName = "kieker-monitoring/kieker";
    String landscapeToken = "test-token";

    DataExporter mockExporter = mock(DataExporter.class);
    System.out.println("#######################################################");
    System.out.println("Calling fetchSocialDataInRange");
    githubSocialFetcherService.fetchSocialDataInRange(
        repositoryName, startDate, endDate, mockExporter, landscapeToken, "dummy-token");
    System.out.println("Finished testFetchSocialDataInRange");
    System.out.println("#######################################################");

  }
}
