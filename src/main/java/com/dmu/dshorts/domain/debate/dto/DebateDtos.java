package com.dmu.dshorts.domain.debate.dto;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;
import java.util.List;

/** Request/response shapes of the debate API. */
public final class DebateDtos {

    private DebateDtos() {
    }

    /** {@code POST /debates}, {@code POST /guest/debates}. Validated in the service (IDs are strings). */
    public record CreateDebateRequest(List<String> personaIds, String topic) {
    }

    /** Public persona fields only (no AI settings). */
    public record PersonaSummary(String id, String username, String displayName, String era,
                                 String avatarPath, String aiDisclosure) {

        public static PersonaSummary from(Persona persona) {
            return new PersonaSummary(Ids.str(persona.getId()), persona.getUsername(), persona.getDisplayName(),
                    persona.getEra(), persona.getAvatarPath(), persona.getAiDisclosure());
        }
    }

    /** One turn. {@code citationType} is always {@code RECONSTRUCTION} (AI reconstruction, not a quote). */
    public record TurnResponse(int order, String personaId, String stage, String body, String citationType) {

        public static final String RECONSTRUCTION = "RECONSTRUCTION";
    }

    /**
     * Debate result. Member: {@code id} set, {@code persisted=true}, {@code remainingToday} = today's remaining
     * quota. Guest: {@code id=null}, {@code persisted=false}, {@code remainingToday=null}.
     * {@code personas} are ordered by persona id (low, high).
     */
    public record DebateResponse(String id, boolean persisted, String topic, List<PersonaSummary> personas,
                                 List<TurnResponse> turns, String summary, Instant completedAt,
                                 Integer remainingToday) {
    }

    /** Item of {@code GET /users/me/debates}. */
    public record DebateListItem(String id, String topic, List<PersonaSummary> personas, String summary,
                                 Instant completedAt) {
    }

    /** {@code GET /users/me/debates}: page plus today's quota (KST day). */
    public record DebateListResponse(List<DebateListItem> items, String nextCursor, int dailyLimit,
                                     int usedToday, int remainingToday, Instant resetAt) {
    }
}
