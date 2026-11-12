package com.dmu.dshorts.domain.generation.command;

import com.dmu.dshorts.domain.generation.service.PostPreparationService;
import com.dmu.dshorts.domain.generation.service.PostPreparationService.PreparationResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Admin preparation command. Inactive unless the app is started with {@code --prepare-posts=<runKey>}:
 * <pre>
 * ./gradlew bootRun --args='--prepare-posts=demo-v1'
 * java -jar build/libs/*.jar --prepare-posts=demo-v1
 * </pre>
 * It runs the preparation for the run key and then exits: 0 when every job of the run key is SUCCEEDED,
 * 1 when some job is not, 2 on a configuration error (invalid run key, AI not configured).
 * Running the same command again only retries FAILED/PENDING jobs.
 */
@Component
@Order(100)
public class PreparePostsCommand implements ApplicationRunner {

    public static final String OPTION = "prepare-posts";

    private static final Logger log = LoggerFactory.getLogger(PreparePostsCommand.class);

    private final PostPreparationService preparationService;
    private final ConfigurableApplicationContext context;

    public PreparePostsCommand(PostPreparationService preparationService, ConfigurableApplicationContext context) {
        this.preparationService = preparationService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPTION)) {
            return;
        }
        List<String> values = args.getOptionValues(OPTION);
        String runKey = values == null || values.isEmpty() ? null : values.get(0);
        int exitCode;
        try {
            PreparationResult result = preparationService.prepare(runKey);
            log.info("prepare-posts '{}': created {} jobs, ran {}, succeeded {}, failed {}; all jobs: {}",
                    result.runKey(), result.createdJobs(), result.attemptedJobs(), result.succeeded(),
                    result.failed(), result.statusCounts());
            exitCode = result.allSucceeded() ? 0 : 1;
            if (exitCode != 0) {
                log.warn("Some jobs are not SUCCEEDED. Re-run the same command to retry only the failed ones.");
            }
        } catch (RuntimeException ex) {
            log.error("prepare-posts '{}' aborted: {}", runKey, ex.getMessage());
            exitCode = 2;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
