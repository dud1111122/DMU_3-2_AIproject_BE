package com.dmu.dshorts.domain.debate.dto;

import com.dmu.dshorts.domain.debate.entity.DebateTurn;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;
import java.util.List;

/** Request/response shapes of the debate API. */
public final class DebateDtos {

    private DebateDtos() {
    }

    /** {@code POST /debates}. IDs are validated in the service. */
    public record CreateDebateRequest(List<String> personaIds, String topic) {
    }

    /** {@code POST /debates/{id}/messages}. */
    public record MessageRequest(String content) {
    }

    /** Public persona fields only (no AI settings). */
    public record PersonaSummary(String id, String username, String displayName, String era,
                                 String avatarPath, String aiDisclosure) {

        public static PersonaSummary from(Persona persona) {
            return new PersonaSummary(Ids.str(persona.getId()), persona.getUsername(), persona.getDisplayName(),
                    persona.getEra(), persona.getAvatarPath(), persona.getAiDisclosure());
        }
    }

    /**
     * One turn. {@code speakerType} is {@code PERSONA} (AI turn, {@code personaId} set) or {@code USER}
     * ({@code personaId} null). {@code order} is 1-based.
     */
    public record TurnResponse(int order, String speakerType, String personaId, String body, Instant createdAt) {

        public static TurnResponse from(DebateTurn turn) {
            return new TurnResponse(turn.getTurnOrder(), turn.getSpeakerType().name(),
                    turn.getSpeakerPersonaId() == null ? null : Ids.str(turn.getSpeakerPersonaId()),
                    turn.getBody(), turn.getCreatedAt());
        }
    }

    /**
     * Debate detail. {@code personas} = [A, B] (A speaks first). {@code summary} is null until ENDED.
     * {@code remainingTurns} = turnLimit - turnCount.
     */
    public record DebateResponse(String id, String status, String topic, List<PersonaSummary> personas,
                                 List<TurnResponse> turns, String summary, int turnCount, int turnLimit,
                                 int remainingTurns, Instant createdAt, Instant endedAt) {
    }

    /** Item of {@code GET /users/me/debates}. */
    public record DebateListItem(String id, String status, String topic, List<PersonaSummary> personas,
                                 int turnCount, Instant createdAt) {
    }

    /** {@code GET /users/me/debates}: page plus today's quota (KST day). */
    public record DebateListResponse(List<DebateListItem> items, String nextCursor, int dailyLimit,
                                     int usedToday, int remainingToday, Instant resetAt) {
    }

    /** SSE {@code delta} event data. */
    public record DeltaEvent(String text) {
    }

    /** SSE {@code error} event data. */
    public record ErrorEvent(String code, String message) {
    }
}
