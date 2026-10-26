package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.CreateDebateRequest;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateListItem;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateListResponse;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateResponse;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.PersonaSummary;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.TurnResponse;
import com.dmu.dshorts.domain.debate.entity.Debate;
import com.dmu.dshorts.domain.debate.entity.DebateTurn;
import com.dmu.dshorts.domain.debate.repository.DebatePersonaQueries;
import com.dmu.dshorts.domain.debate.repository.DebateRepository;
import com.dmu.dshorts.domain.debate.repository.DebateTurnRepository;
import com.dmu.dshorts.domain.debate.service.DebateGenerator.GeneratedDebate;
import com.dmu.dshorts.domain.debate.service.DebateGenerator.GeneratedTurn;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persona debates.
 *
 * <p>Member create flow: validate → reuse lookup (200) → per-user in-process lock (busy → 409) → reuse lookup
 * again → KST daily limit (429) → one AI call outside any transaction → validate → one transaction that
 * re-checks the KST day/limit at success time and saves debate + turns with {@code completedAt} = success time
 * (201). AI or save failure → 502/503 and nothing is stored, so no quota is used.
 * Single backend process assumption (see 기획.md §8): the lock is not distributed.
 */
@Service
public class DebateService {

    public static final int DAILY_LIMIT = 3;

    private static final Logger log = LoggerFactory.getLogger(DebateService.class);

    private final DebateRepository debateRepository;
    private final DebateTurnRepository turnRepository;
    private final DebatePersonaQueries personaQueries;
    private final VisibilityService visibilityService;
    private final DebateGenerator generator;
    private final DebateClock clock;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Set<Long> inProgressUsers = ConcurrentHashMap.newKeySet();

    public DebateService(DebateRepository debateRepository, DebateTurnRepository turnRepository,
                         DebatePersonaQueries personaQueries, VisibilityService visibilityService,
                         DebateGenerator generator, DebateClock clock,
                         PlatformTransactionManager transactionManager) {
        this.debateRepository = debateRepository;
        this.turnRepository = turnRepository;
        this.personaQueries = personaQueries;
        this.visibilityService = visibilityService;
        this.generator = generator;
        this.clock = clock;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** Result of a member create: {@code created=false} means an existing own result was reused (200). */
    public record CreateResult(DebateResponse debate, boolean created) {
    }

    public CreateResult create(Long userId, CreateDebateRequest request) {
        DebateInput input = DebateInput.from(request);
        Persona first = visibilityService.getVisiblePersona(input.firstPersonaId());
        Persona second = visibilityService.getVisiblePersona(input.secondPersonaId());

        Optional<DebateResponse> reused = findReusable(userId, input);
        if (reused.isPresent()) {
            return new CreateResult(reused.get(), false);
        }
        if (!inProgressUsers.add(userId)) {
            throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        try {
            reused = findReusable(userId, input);
            if (reused.isPresent()) {
                return new CreateResult(reused.get(), false);
            }
            Instant startedAt = clock.now();
            Long usedBefore = readTx.execute(status -> countUsed(userId, startedAt));
            if (usedBefore >= DAILY_LIMIT) {
                throw limitExceeded(startedAt);
            }

            GeneratedDebate generated = generator.generate(first, second, input.topic());

            Instant successAt = clock.now();
            return new CreateResult(save(userId, input, generated, successAt), true);
        } finally {
            inProgressUsers.remove(userId);
        }
    }

    /** Guest: same validation and generation, nothing is written. */
    public DebateResponse createGuest(CreateDebateRequest request) {
        DebateInput input = DebateInput.from(request);
        Persona first = visibilityService.getVisiblePersona(input.firstPersonaId());
        Persona second = visibilityService.getVisiblePersona(input.secondPersonaId());
        GeneratedDebate generated = generator.generate(first, second, input.topic());
        List<TurnResponse> turns = new ArrayList<>();
        int order = 1;
        for (GeneratedTurn turn : generated.turns()) {
            turns.add(new TurnResponse(order++, Ids.str(turn.speakerId()), turn.stage().name(), turn.body(),
                    TurnResponse.RECONSTRUCTION));
        }
        List<PersonaSummary> personas = sortedById(List.of(first, second)).stream().map(PersonaSummary::from).toList();
        return new DebateResponse(null, false, input.topic(), personas, turns, generated.summary(), clock.now(), null);
    }

    public DebateResponse get(Long userId, Long debateId) {
        return readTx.execute(status -> {
            Debate debate = debateRepository.findByIdAndUserId(debateId, userId).orElseThrow(ApiException::notFound);
            if (!visibilityService.isDebateVisible(debate.getPersonaLowId(), debate.getPersonaHighId())) {
                throw ApiException.notFound();
            }
            return toResponse(debate, remaining(countUsed(userId, clock.now())));
        });
    }

    public DebateListResponse list(Long userId, String cursorParam, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        Cursors.TimeIdCursor cursor = Cursors.decodeTimeId(cursorParam);
        return readTx.execute(status -> {
            PageRequest page = PageRequest.ofSize(limit + 1);
            List<Debate> rows = cursor == null
                    ? debateRepository.findVisibleFirstPage(userId, page)
                    : debateRepository.findVisibleAfter(userId, cursor.time(), cursor.id(), page);
            Set<Long> personaIds = new HashSet<>();
            rows.forEach(d -> {
                personaIds.add(d.getPersonaLowId());
                personaIds.add(d.getPersonaHighId());
            });
            Map<Long, PersonaSummary> personas = personaSummaries(personaIds);
            PageResponse<DebateListItem> slice = PageResponse.slice(rows, limit,
                    d -> Cursors.encode(d.getCompletedAt(), d.getId()),
                    d -> new DebateListItem(Ids.str(d.getId()), d.getTopicText(),
                            List.of(personas.get(d.getPersonaLowId()), personas.get(d.getPersonaHighId())),
                            d.getIssueSummary(), d.getCompletedAt()));
            Instant now = clock.now();
            int used = (int) Math.min(countUsed(userId, now), Integer.MAX_VALUE);
            return new DebateListResponse(slice.items(), slice.nextCursor(), DAILY_LIMIT, used, remaining(used),
                    DebateClock.dayEnd(now));
        });
    }

    private Optional<DebateResponse> findReusable(Long userId, DebateInput input) {
        return readTx.execute(status -> debateRepository
                .findByUserIdAndPersonaLowIdAndPersonaHighIdAndTopicKey(
                        userId, input.lowId(), input.highId(), input.topicKey())
                .map(debate -> toResponse(debate, remaining(countUsed(userId, clock.now())))));
    }

    private DebateResponse save(Long userId, DebateInput input, GeneratedDebate generated, Instant successAt) {
        try {
            return writeTx.execute(status -> {
                long used = countUsed(userId, successAt);
                if (used >= DAILY_LIMIT) {
                    throw limitExceeded(successAt);
                }
                Debate debate = new Debate(userId, input.lowId(), input.highId(), input.topic(), input.topicKey(),
                        generated.summary());
                debate.setAiModel(truncate(generated.aiModel(), 100));
                debate.setCompletedAt(successAt);
                debateRepository.saveAndFlush(debate);
                List<DebateTurn> turns = new ArrayList<>();
                short order = 1;
                for (GeneratedTurn turn : generated.turns()) {
                    turns.add(new DebateTurn(debate.getId(), order++, input.lowId(), input.highId(),
                            turn.speakerId(), turn.stage(), turn.body()));
                }
                turnRepository.saveAll(turns);
                turnRepository.flush();
                return toResponse(debate, turns, remaining(used + 1));
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Saving debate failed for user {}", userId, ex);
            throw new ApiException(ErrorCode.AI_FAILED, "대담 결과를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private long countUsed(Long userId, Instant at) {
        return debateRepository.countByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                userId, DebateClock.dayStart(at), DebateClock.dayEnd(at));
    }

    private static int remaining(long used) {
        return (int) Math.max(0, DAILY_LIMIT - used);
    }

    private static ApiException limitExceeded(Instant at) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("remainingToday", 0);
        details.put("resetAt", DebateClock.dayEnd(at));
        return new ApiException(ErrorCode.DAILY_LIMIT_EXCEEDED, "오늘 새 대담은 하루 3회까지 만들 수 있습니다.")
                .withDetails(details);
    }

    private DebateResponse toResponse(Debate debate, Integer remainingToday) {
        return toResponse(debate, turnRepository.findByDebateIdOrderByTurnOrderAsc(debate.getId()), remainingToday);
    }

    private DebateResponse toResponse(Debate debate, List<DebateTurn> turns, Integer remainingToday) {
        Map<Long, PersonaSummary> personas = personaSummaries(List.of(debate.getPersonaLowId(), debate.getPersonaHighId()));
        List<TurnResponse> turnDtos = turns.stream()
                .sorted(Comparator.comparing(DebateTurn::getTurnOrder))
                .map(t -> new TurnResponse(t.getTurnOrder(), Ids.str(t.getSpeakerId()), t.getStage().name(),
                        t.getBody(), TurnResponse.RECONSTRUCTION))
                .toList();
        return new DebateResponse(Ids.str(debate.getId()), true, debate.getTopicText(),
                List.of(personas.get(debate.getPersonaLowId()), personas.get(debate.getPersonaHighId())),
                turnDtos, debate.getIssueSummary(), debate.getCompletedAt(), remainingToday);
    }

    private Map<Long, PersonaSummary> personaSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return personaQueries.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Persona::getId, PersonaSummary::from, (a, b) -> a));
    }

    private static List<Persona> sortedById(List<Persona> personas) {
        return personas.stream().sorted(Comparator.comparing(Persona::getId)).toList();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
