package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.generation.dto.GenerationJobResponse;
import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import com.dmu.dshorts.domain.generation.entity.GenerationJobStatus;
import com.dmu.dshorts.domain.generation.repository.GenerationJobRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin list of preparation jobs: newest first (id desc), optional status filter, id cursor. */
@Service
public class GenerationJobQueryService {

    private final GenerationJobRepository jobRepository;
    private final PersonaRepository personaRepository;
    private final TopicRepository topicRepository;

    public GenerationJobQueryService(GenerationJobRepository jobRepository,
                                     PersonaRepository personaRepository,
                                     TopicRepository topicRepository) {
        this.jobRepository = jobRepository;
        this.personaRepository = personaRepository;
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<GenerationJobResponse> list(String statusParam, String cursor, Integer limitParam) {
        GenerationJobStatus status = parseStatus(statusParam);
        Long afterId = Cursors.decodeId(cursor);
        int limit = Cursors.limit(limitParam);
        Pageable page = PageRequest.ofSize(limit + 1);

        List<GenerationJob> rows;
        if (status == null) {
            rows = afterId == null ? jobRepository.findAllByOrderByIdDesc(page)
                    : jobRepository.findByIdLessThanOrderByIdDesc(afterId, page);
        } else {
            rows = afterId == null ? jobRepository.findByStatusOrderByIdDesc(status, page)
                    : jobRepository.findByStatusAndIdLessThanOrderByIdDesc(status, afterId, page);
        }

        Map<Long, String> personaNames = personaRepository
                .findAllById(rows.stream().map(GenerationJob::getPersonaId).distinct().toList()).stream()
                .collect(Collectors.toMap(Persona::getId, Persona::getUsername));
        Map<Long, String> topicNames = topicRepository
                .findAllById(rows.stream().map(GenerationJob::getTopicId).distinct().toList()).stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName));
        Function<GenerationJob, GenerationJobResponse> mapper = job -> GenerationJobResponse.from(job,
                personaNames.get(job.getPersonaId()), topicNames.get(job.getTopicId()));
        return PageResponse.slice(rows, limit, job -> Cursors.encodeId(job.getId()), mapper);
    }

    private static GenerationJobStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GenerationJobStatus.valueOf(raw.strip());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("status", "status는 PENDING, RUNNING, SUCCEEDED, FAILED 중 하나여야 합니다.");
        }
    }
}
