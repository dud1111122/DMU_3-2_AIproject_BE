package com.dmu.dshorts.domain.study.service;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.global.visibility.VisibilityService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds a {@link StudyPostContext} for a visible post (404 otherwise). Always rebuilt from the DB. */
@Component
public class StudyContextLoader {

    private final VisibilityService visibilityService;
    private final PostSectionRepository sectionRepository;
    private final KeyPointRepository keyPointRepository;
    private final PostSourceRepository sourceRepository;

    public StudyContextLoader(VisibilityService visibilityService, PostSectionRepository sectionRepository,
                              KeyPointRepository keyPointRepository, PostSourceRepository sourceRepository) {
        this.visibilityService = visibilityService;
        this.sectionRepository = sectionRepository;
        this.keyPointRepository = keyPointRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional(readOnly = true)
    public StudyPostContext load(Long postId) {
        Post post = visibilityService.getVisiblePost(postId);
        Persona persona = post.getPersona();
        return new StudyPostContext(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                new StudyPostContext.PersonaSettings(persona.getId(), persona.getDisplayName(), persona.getEra(),
                        persona.getAiDisclosure(), persona.getPerspective(), persona.getVoice(),
                        persona.getKnowledgeScope(), persona.getProhibitions(), persona.getConversationRules()),
                sectionRepository.findByPostIdOrderBySectionOrderAsc(post.getId()).stream()
                        .map(s -> new StudyPostContext.Section(s.getSectionOrder(), s.getTitle(), s.getBody(),
                                s.getCitationType() == null ? null : s.getCitationType().name(),
                                s.getQuoteAttribution()))
                        .toList(),
                keyPointRepository.findByPostIdOrderByPointOrderAsc(post.getId()).stream()
                        .map(k -> new StudyPostContext.Point(k.getId(), k.getPointOrder(), k.getText()))
                        .toList(),
                sourceRepository.findByPostIdOrderBySourceOrderAsc(post.getId()).stream()
                        .map(s -> new StudyPostContext.Source(s.getTitle(), s.getAuthor(), s.getPublisher(),
                                s.getPublicationLabel(), s.getUrl(), s.isVerified()))
                        .toList());
    }
}
