package com.dmu.dshorts.domain.generation.dto;

import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;

/** Admin view of one preparation job. IDs are strings; {@code resultPostId} is set only when SUCCEEDED. */
public record GenerationJobResponse(String id, String requestKey, String personaId, String personaUsername,
                                    String topicId, String topicName, String subject, String status,
                                    int attemptCount, String aiModel, String errorMessage, String resultPostId,
                                    Instant createdAt, Instant finishedAt) {

    public static GenerationJobResponse from(GenerationJob job, String personaUsername, String topicName) {
        return new GenerationJobResponse(Ids.str(job.getId()), job.getRequestKey(), Ids.str(job.getPersonaId()),
                personaUsername, Ids.str(job.getTopicId()), topicName, job.getSubject(), job.getStatus().name(),
                job.getAttemptCount(), job.getAiModel(), job.getErrorMessage(), Ids.str(job.getResultPostId()),
                job.getCreatedAt(), job.getFinishedAt());
    }
}
