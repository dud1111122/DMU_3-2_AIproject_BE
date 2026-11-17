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
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Debate creation, detail and list. Creating a debate only stores the pair and topic (no AI call).
 * Turns, messages and the end action live in {@link DebateConversationService}.
 *
 * <p>Daily quota: at most {@link DebateRules#DAILY_LIMIT} debates created per member per KST day, counted by
 * {@code created_at}. A failed create stores nothing and does not count. Per-user in-process lock keeps the
 * count and insert consistent (single backend process assumption).
 */
@Service
public class DebateService {

    private final DebateRepository debateRepository;
    private final DebateTurnRepository turnRepository;
    private final DebatePersonaQueries personaQueries;
    private final VisibilityService visibilityService;
    private final DebateClock clock;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Set<Long> creatingUsers = ConcurrentHashMap.newKeySet();

    public DebateService(DebateRepository debateRepository, DebateTurnRepository turnRepository,
                         DebatePersonaQueries personaQueries, VisibilityService visibilityService,
                         DebateClock clock, PlatformTransactionManager transactionManager) {
        this.debateRepository = debateRepository;
        this.turnRepository = turnRepository;
        this.personaQueries = personaQueries;
        this.visibilityService = visibilityService;
        this.clock = clock;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** {@code POST /debates}: 201 with the new debate (no turns yet). */
    public DebateResponse create(Long userId, CreateDebateRequest request) {
        DebateInput input = DebateInput.from(request);
        Persona first = visibilityService.getVisiblePersona(input.personaAId());
        Persona second = visibilityService.getVisiblePersona(input.personaBId());
        if (!creatingUsers.add(userId)) {
            throw DebateRules.requestInProgress();
        }
        try {
            Debate saved = writeTx.execute(status -> {
                Instant now = clock.now();
                if (countCreatedOn(userId, now) >= DebateRules.DAILY_LIMIT) {
                    throw limitExceeded(now);
                }
                Debate debate = new Debate(userId, first.getId(), second.getId(), input.topic());
                debate.setCreatedAt(now);
                debate.setUpdatedAt(now);
                return debateRepository.save(debate);
            });
            return toResponse(saved, List.of(PersonaSummary.from(first), PersonaSummary.from(second)), List.of());
        } finally {
            creatingUsers.remove(userId);
        }
    }

    /** {@code GET /debates/{id}}: own debate with both personas visible, else 404. */
    public DebateResponse get(Long userId, Long debateId) {
        return readTx.execute(status -> detail(findOwnedVisible(userId, debateId)));
    }

    /** {@code GET /users/me/debates}: own visible debates, newest first, plus today's quota. */
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
                personaIds.add(d.getPersonaAId());
                personaIds.add(d.getPersonaBId());
            });
            Map<Long, Persona> personas = personasById(personaIds);
            PageResponse<DebateListItem> slice = PageResponse.slice(rows, limit,
                    d -> Cursors.encode(d.getCreatedAt(), d.getId()),
                    d -> new DebateListItem(Ids.str(d.getId()), d.getStatus().name(), d.getTopic(),
                            summaries(personas, d.getPersonaAId(), d.getPersonaBId()),
                            d.getTurnCount(), d.getCreatedAt()));
            Instant now = clock.now();
            int used = (int) Math.min(countCreatedOn(userId, now), Integer.MAX_VALUE);
            return new DebateListResponse(slice.items(), slice.nextCursor(), DebateRules.DAILY_LIMIT, used,
                    remaining(used), DebateClock.dayEnd(now));
        });
    }

    /** Own debate whose two personas are both visible; otherwise 404 (other users and hidden alike). */
    Debate findOwnedVisible(Long userId, Long debateId) {
        Debate debate = debateRepository.findByIdAndUserId(debateId, userId).orElseThrow(ApiException::notFound);
        if (!visibilityService.isDebateVisible(debate.getPersonaAId(), debate.getPersonaBId())) {
            throw ApiException.notFound();
        }
        return debate;
    }

    /** Full detail with turns, call inside a transaction or on a freshly loaded debate. */
    DebateResponse detail(Debate debate) {
        Map<Long, Persona> personas = personasById(List.of(debate.getPersonaAId(), debate.getPersonaBId()));
        List<DebateTurn> turns = turnRepository.findByDebateIdOrderByTurnOrderAsc(debate.getId());
        return toResponse(debate,
                summaries(personas, debate.getPersonaAId(), debate.getPersonaBId()), turns.stream()
                        .map(TurnResponse::from).toList());
    }

    /** Personas by id, regardless of visibility (callers check visibility first). */
    Map<Long, Persona> personasById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return personaQueries.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Persona::getId, Function.identity(), (a, b) -> a));
    }

    static DebateResponse toResponse(Debate debate, List<PersonaSummary> personas, List<TurnResponse> turns) {
        int count = debate.getTurnCount();
        return new DebateResponse(Ids.str(debate.getId()), debate.getStatus().name(), debate.getTopic(), personas,
                turns, debate.getSummary(), count, DebateRules.TURN_LIMIT,
                Math.max(0, DebateRules.TURN_LIMIT - count), debate.getCreatedAt(), debate.getEndedAt());
    }

    private static List<PersonaSummary> summaries(Map<Long, Persona> personas, Long personaAId, Long personaBId) {
        return List.of(PersonaSummary.from(personas.get(personaAId)), PersonaSummary.from(personas.get(personaBId)));
    }

    private long countCreatedOn(Long userId, Instant at) {
        return debateRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, DebateClock.dayStart(at), DebateClock.dayEnd(at));
    }

    private static int remaining(long used) {
        return (int) Math.max(0, DebateRules.DAILY_LIMIT - used);
    }

    private static ApiException limitExceeded(Instant at) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("remainingToday", 0);
        details.put("resetAt", DebateClock.dayEnd(at));
        return new ApiException(ErrorCode.DAILY_LIMIT_EXCEEDED, "오늘 새 대담은 하루 3회까지 만들 수 있습니다.")
                .withDetails(details);
    }
}
