package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.generation.seed.SeedData.KeyPointSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.QuestionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SectionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SourceSeed;
import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.CitationType;
import com.dmu.dshorts.domain.post.entity.GroundingMode;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostSource;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single entry point that stores a complete post: post + post_topics + cards + key points + one question with
 * 4 options per point + sources (always {@code verified=false}). Must run inside the caller's transaction so that
 * the deferred {@code questions → answer_options} FK is checked at COMMIT together with everything else.
 * Callers validate the draft first with {@link PostDraftValidator}.
 */
@Component
public class PostWriter {

    private final PostRepository postRepository;
    private final PostSectionRepository sectionRepository;
    private final KeyPointRepository keyPointRepository;
    private final PostSourceRepository sourceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;

    public PostWriter(PostRepository postRepository,
                      PostSectionRepository sectionRepository,
                      KeyPointRepository keyPointRepository,
                      PostSourceRepository sourceRepository,
                      QuestionRepository questionRepository,
                      AnswerOptionRepository answerOptionRepository) {
        this.postRepository = postRepository;
        this.sectionRepository = sectionRepository;
        this.keyPointRepository = keyPointRepository;
        this.sourceRepository = sourceRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Post save(PostSeed draft, Persona persona, Collection<Topic> topics, Instant publishedAt, String aiModel) {
        Post post = new Post(persona, draft.title().strip());
        post.setSummary(nullToEmpty(draft.summary()));
        post.setCaption(nullToEmpty(draft.caption()));
        post.setGroundingMode(GroundingMode.MODEL_KNOWLEDGE);
        post.setAiModel(aiModel);
        post.setPublishedAt(publishedAt);
        post.getTopics().addAll(topics);
        postRepository.save(post);
        Long postId = post.getId();

        List<Long> sectionIds = new ArrayList<>();
        for (int i = 0; i < draft.sections().size(); i++) {
            SectionSeed seed = draft.sections().get(i);
            PostSection section = new PostSection(postId, (short) (i + 1), seed.body().strip());
            section.setTitle(blankToNull(seed.title()));
            section.setCitationType(seed.citationType() == null
                    ? CitationType.RECONSTRUCTION
                    : CitationType.valueOf(seed.citationType()));
            section.setQuoteAttribution(section.getCitationType() == CitationType.DIRECT_QUOTE
                    ? seed.quoteAttribution().strip()
                    : blankToNull(seed.quoteAttribution()));
            section.setImagePath(seed.imagePath());
            section.setImageAlt(seed.imageAlt());
            section.setImageMime(seed.imageMime());
            section.setImageWidth(seed.imageWidth());
            section.setImageHeight(seed.imageHeight());
            section.setImageAiGenerated(Boolean.TRUE.equals(seed.imageAiGenerated()));
            section.setImageCredit(seed.imageCredit());
            sectionIds.add(sectionRepository.save(section).getId());
        }

        for (int i = 0; i < draft.keyPoints().size(); i++) {
            KeyPointSeed pointSeed = draft.keyPoints().get(i);
            KeyPoint keyPoint = new KeyPoint(postId, (short) (i + 1), pointSeed.text().strip());
            if (pointSeed.sectionOrder() != null) {
                keyPoint.setSectionId(sectionIds.get(pointSeed.sectionOrder() - 1));
            }
            keyPointRepository.save(keyPoint);

            QuestionSeed questionSeed = pointSeed.question();
            // questions.correct_option_order → answer_options is DEFERRABLE: options are inserted in the same tx.
            Question question = questionRepository.save(new Question(postId, keyPoint.getId(),
                    questionSeed.prompt().strip(), questionSeed.explanation().strip(),
                    (short) questionSeed.correctOptionOrder()));
            for (int j = 0; j < questionSeed.options().size(); j++) {
                answerOptionRepository.save(new AnswerOption(question.getId(), (short) (j + 1),
                        questionSeed.options().get(j).strip()));
            }
        }

        for (int i = 0; i < draft.sources().size(); i++) {
            SourceSeed sourceSeed = draft.sources().get(i);
            PostSource source = new PostSource(postId, (short) (i + 1), sourceSeed.title().strip());
            source.setAuthor(blankToNull(sourceSeed.author()));
            source.setPublisher(blankToNull(sourceSeed.publisher()));
            source.setUrl(blankToNull(sourceSeed.url()));
            source.setPublicationLabel(blankToNull(sourceSeed.publicationLabel()));
            source.setNote(blankToNull(sourceSeed.note()));
            source.setVerified(false);
            sourceRepository.save(source);
        }
        return post;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
