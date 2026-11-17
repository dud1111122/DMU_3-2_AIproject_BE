package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateResponse;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.MessageRequest;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.TurnResponse;
import com.dmu.dshorts.domain.debate.entity.Debate;
import com.dmu.dshorts.domain.debate.entity.DebateStatus;
import com.dmu.dshorts.domain.debate.entity.DebateTurn;
import com.dmu.dshorts.domain.debate.entity.SpeakerType;
import com.dmu.dshorts.domain.debate.repository.DebateRepository;
import com.dmu.dshorts.domain.debate.repository.DebateTurnRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.ai.AiMessage;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Live turns of a debate: user messages, streamed AI turns, the end action and the automatic end at 12 turns.
 *
 * <p>Every operation that changes a debate holds an in-process lock for that debate. While it is held,
 * another message, AI turn or end gets 409 REQUEST_IN_PROGRESS. The lock is released before the final SSE event
 * is sent. State is re-checked under the lock, and each turn is inserted in its own short transaction. No DB
 * transaction is open during an AI call. Single backend process assumption: the lock is not distributed.
 */
@Service
public class DebateConversationService {

    private static final Logger log = LoggerFactory.getLogger(DebateConversationService.class);
    private static final int MODEL_MAX_CHARS = 100;

    private final DebateRepository debateRepository;
    private final DebateTurnRepository turnRepository;
    private final DebateService debateService;
    private final DebateTurnGenerator generator;
    private final DebateClock clock;
    private final AiClient aiClient;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Set<Long> busyDebates = ConcurrentHashMap.newKeySet();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(daemonThreads());

    public DebateConversationService(DebateRepository debateRepository, DebateTurnRepository turnRepository,
                                     DebateService debateService, DebateTurnGenerator generator, DebateClock clock,
                                     AiClient aiClient, PlatformTransactionManager transactionManager) {
        this.debateRepository = debateRepository;
        this.turnRepository = turnRepository;
        this.debateService = debateService;
        this.generator = generator;
        this.clock = clock;
        this.aiClient = aiClient;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    /**
     * {@code POST /debates/{id}/messages}: stores the user's turn. Reaching 12 turns also ends the debate.
     * If the summary call then fails, the turn stays stored and the debate stays ACTIVE (use {@code /end}).
     */
    public TurnResponse postMessage(Long userId, Long debateId, MessageRequest request) {
        String content = DebateInput.messageContent(request);
        Long id = debateService.findOwnedVisible(userId, debateId).getId();
        acquire(id);
        try {
            DebateTurn turn = appendTurn(id, SpeakerType.USER, null, content, null);
            if (turn.getTurnOrder() == DebateRules.TURN_LIMIT) {
                try {
                    summarizeAndEnd(id);
                } catch (ApiException ex) {
                    log.warn("Summary failed after the 12th turn of debate {}: {}", id, ex.getCode());
                }
            }
            return TurnResponse.from(turn);
        } finally {
            release(id);
        }
    }

    /**
     * {@code POST /debates/{id}/turns} (SSE). Checks run synchronously, so ENDED, limit, lock and not-found
     * errors throw here as normal JSON errors. After that the AI turn is generated on a worker thread and
     * reported through {@code listener}.
     */
    public void streamNextTurn(Long userId, Long debateId, TurnStreamListener listener) {
        Long id = debateService.findOwnedVisible(userId, debateId).getId();
        generator.requireConfigured();
        acquire(id);
        try {
            AiTurnPlan plan = planAiTurn(id);
            streamExecutor.execute(() -> runAiTurn(plan, listener));
        } catch (RuntimeException ex) {
            release(id);
            throw ex;
        }
    }

    /**
     * {@code POST /debates/{id}/end}: generates the summary (one AI call, outside any transaction) and marks
     * the debate ENDED. Needs at least one turn. On AI failure nothing changes (502).
     */
    public DebateResponse end(Long userId, Long debateId) {
        Long id = debateService.findOwnedVisible(userId, debateId).getId();
        acquire(id);
        try {
            Debate debate = debateRepository.findById(id).orElseThrow(ApiException::notFound);
            if (debate.getStatus() == DebateStatus.ENDED) {
                throw DebateRules.debateEnded();
            }
            if (debate.getTurnCount() == 0) {
                throw ApiException.badRequest("발언이 한 번 이상 있어야 대담을 종료할 수 있습니다.");
            }
            summarizeAndEnd(id);
        } finally {
            release(id);
        }
        return debateService.get(userId, id);
    }

    private void runAiTurn(AiTurnPlan plan, TurnStreamListener listener) {
        DebateTurn stored = null;
        ApiException failure = null;
        try {
            String body = generator.streamTurn(plan.messages(), listener::delta);
            stored = appendTurn(plan.debateId(), SpeakerType.PERSONA, plan.speakerId(), body,
                    truncate(aiClient.modelName()));
            if (stored.getTurnOrder() == DebateRules.TURN_LIMIT) {
                try {
                    summarizeAndEnd(plan.debateId());
                } catch (ApiException ex) {
                    failure = new ApiException(ErrorCode.AI_FAILED,
                            "12턴에 도달했지만 요약 생성에 실패했습니다. 대담 종료를 다시 시도해주세요.");
                }
            }
        } catch (ApiException ex) {
            failure = ex;
        } catch (RuntimeException ex) {
            log.error("Debate turn failed for debate {}", plan.debateId(), ex);
            failure = new ApiException(ErrorCode.INTERNAL_ERROR);
        } finally {
            release(plan.debateId());
        }
        if (stored != null) {
            listener.turn(TurnResponse.from(stored));
        }
        if (failure != null) {
            listener.error(failure);
        }
        listener.complete();
    }

    /** Re-checks state under the lock and builds the prompt. Read-only transaction. */
    private AiTurnPlan planAiTurn(Long debateId) {
        return readTx.execute(status -> {
            Debate debate = debateRepository.findById(debateId).orElseThrow(ApiException::notFound);
            DebateRules.requireActive(debate);
            List<DebateTurn> turns = turnRepository.findByDebateIdOrderByTurnOrderAsc(debateId);
            Map<Long, Persona> personas = debateService.personasById(
                    List.of(debate.getPersonaAId(), debate.getPersonaBId()));
            Persona first = personas.get(debate.getPersonaAId());
            Persona second = personas.get(debate.getPersonaBId());
            Long speakerId = nextSpeakerId(debate, turns);
            List<AiMessage> messages = DebateTurnGenerator.turnMessages(first, second, personas.get(speakerId),
                    debate.getTopic(), transcript(turns, personas));
            return new AiTurnPlan(debateId, speakerId, messages);
        });
    }

    /** A speaks first; afterwards the persona that did not speak the last AI turn. User turns do not count. */
    private static Long nextSpeakerId(Debate debate, List<DebateTurn> turns) {
        for (int i = turns.size() - 1; i >= 0; i--) {
            DebateTurn turn = turns.get(i);
            if (turn.getSpeakerType() == SpeakerType.PERSONA) {
                return turn.getSpeakerPersonaId().equals(debate.getPersonaAId())
                        ? debate.getPersonaBId()
                        : debate.getPersonaAId();
            }
        }
        return debate.getPersonaAId();
    }

    /**
     * Generates the summary and stores it with status ENDED. Caller holds the lock. Throws AI_FAILED (502) and
     * leaves the debate unchanged when the AI call fails.
     */
    private void summarizeAndEnd(Long debateId) {
        List<AiMessage> messages = readTx.execute(status -> {
            Debate debate = debateRepository.findById(debateId).orElseThrow(ApiException::notFound);
            if (debate.getStatus() == DebateStatus.ENDED) {
                throw DebateRules.debateEnded();
            }
            List<DebateTurn> all = turnRepository.findByDebateIdOrderByTurnOrderAsc(debateId);
            Map<Long, Persona> personas = debateService.personasById(
                    List.of(debate.getPersonaAId(), debate.getPersonaBId()));
            return DebateTurnGenerator.summaryMessages(personas.get(debate.getPersonaAId()),
                    personas.get(debate.getPersonaBId()), debate.getTopic(), transcript(all, personas));
        });
        DebateTurnGenerator.Summary summary = generator.summarize(messages);
        writeTx.executeWithoutResult(status -> {
            Debate debate = debateRepository.findById(debateId).orElseThrow(ApiException::notFound);
            if (debate.getStatus() == DebateStatus.ENDED) {
                throw DebateRules.debateEnded();
            }
            Instant now = clock.now();
            debate.setSummary(summary.text());
            debate.setAiModel(truncate(summary.model()));
            debate.setStatus(DebateStatus.ENDED);
            debate.setEndedAt(now);
        });
    }

    /**
     * Inserts one turn in its own transaction and bumps {@code turnCount}. The state check runs again here,
     * under the lock. The unique (debate_id, turn_order) constraint is the last guard.
     */
    private DebateTurn appendTurn(Long debateId, SpeakerType type, Long personaId, String body, String aiModel) {
        try {
            return writeTx.execute(status -> {
                Debate debate = debateRepository.findById(debateId).orElseThrow(ApiException::notFound);
                DebateRules.requireActive(debate);
                int order = debate.getTurnCount() + 1;
                DebateTurn turn = new DebateTurn(debateId, order, type, personaId, body, aiModel);
                turn.setCreatedAt(clock.now());
                DebateTurn saved = turnRepository.saveAndFlush(turn);
                debate.setTurnCount(order);
                return saved;
            });
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Saving debate turn failed for debate {}", debateId, ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "대담 발언을 저장하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private void acquire(Long debateId) {
        if (!busyDebates.add(debateId)) {
            throw DebateRules.requestInProgress();
        }
    }

    private void release(Long debateId) {
        busyDebates.remove(debateId);
    }

    private static List<DebateTurnGenerator.Line> transcript(List<DebateTurn> turns, Map<Long, Persona> personas) {
        List<DebateTurnGenerator.Line> lines = new ArrayList<>(turns.size());
        for (DebateTurn turn : turns) {
            String speaker = turn.getSpeakerType() == SpeakerType.USER
                    ? "사용자"
                    : personas.get(turn.getSpeakerPersonaId()).getDisplayName();
            lines.add(new DebateTurnGenerator.Line(speaker, turn.getBody()));
        }
        return lines;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MODEL_MAX_CHARS ? value : value.substring(0, MODEL_MAX_CHARS);
    }

    private static ThreadFactory daemonThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "debate-turn");
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Everything the worker thread needs, prepared under the lock. */
    private record AiTurnPlan(Long debateId, Long speakerId, List<AiMessage> messages) {
    }
}
