package com.dmu.dshorts.domain.study.service;

import com.dmu.dshorts.domain.study.dto.GuestChatRequest;
import com.dmu.dshorts.domain.study.dto.GuestChatResponse;
import com.dmu.dshorts.global.common.Ids;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Guest post chat. Writes nothing to the DB. Persona settings, post and key points are rebuilt from the DB on
 * every call; the client history is only passed as user/assistant turns (role and length validated).
 */
@Service
public class GuestChatService {

    private final StudyContextLoader contextLoader;
    private final StudyChatAi chatAi;

    public GuestChatService(StudyContextLoader contextLoader, StudyChatAi chatAi) {
        this.contextLoader = contextLoader;
        this.chatAi = chatAi;
    }

    /** Not transactional: the context loader's read-only transaction ends before the AI call. */
    public GuestChatResponse chat(Long postId, GuestChatRequest request) {
        StudyPostContext context = contextLoader.load(postId);
        List<StudyChatAi.Turn> history = request.historyOrEmpty().stream()
                .map(item -> new StudyChatAi.Turn("USER".equals(item.role()), item.content()))
                .toList();
        StudyChatAi.Reply reply = chatAi.reply(context, history, request.content().strip());
        return new GuestChatResponse(
                reply.reply(),
                true,
                context.persona().aiDisclosure(),
                reply.assessments().stream()
                        .map(a -> new GuestChatResponse.Assessment(Ids.str(a.keyPointId()), a.level().name(),
                                a.feedback()))
                        .toList());
    }
}
