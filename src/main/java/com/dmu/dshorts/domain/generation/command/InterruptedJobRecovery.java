package com.dmu.dshorts.domain.generation.command;

import com.dmu.dshorts.domain.generation.service.PostPreparationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On every startup, jobs still RUNNING belong to a process that stopped mid-job: mark them FAILED so the next
 * {@code --prepare-posts} run retries them (기획.md §6). Runs before {@link PreparePostsCommand}.
 */
@Component
@Order(5)
public class InterruptedJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InterruptedJobRecovery.class);

    private final PostPreparationService preparationService;

    public InterruptedJobRecovery(PostPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int count = preparationService.failInterruptedJobs();
        if (count > 0) {
            log.warn("Marked {} interrupted RUNNING generation job(s) as FAILED", count);
        }
    }
}
