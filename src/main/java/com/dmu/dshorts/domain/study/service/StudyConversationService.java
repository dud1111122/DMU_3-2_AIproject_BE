package com.dmu.dshorts.domain.study.service;

import com.dmu.dshorts.domain.learning.entity.ReviewReason;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.study.dto.AssessmentResponse;
import com.dmu.dshorts.domain.study.dto.SendMessageResponse;
import com.dmu.dshorts.domain.study.dto.StudyConversationResponse;
import com.dmu.dshorts.domain.study.dto.StudyMessageResponse;
import com.dmu.dshorts.domain.study.entity.AssessmentLevel;
import com.dmu.dshorts.domain.study.entity.ConversationMessage;
import com.dmu.dshorts.domain.study.entity.LearningAssessment;
import com.dmu.dshorts.domain.study.entity.LearningAssessmentId;
import com.dmu.dshorts.domain.study.entity.MessageRole;
import com.dmu.dshorts.domain.study.entity.StudyConversation;
import com.dmu.dshorts.domain.study.repository.ConversationMessageRepository;
import com.dmu.dshorts.domain.study.repository.LearningAssessmentRepository;
import com.dmu.dshorts.domain.study.repository.StudyConversationRepository;
import com.dmu.dshorts.domain.study.repository.StudyReviewItemCommands;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Member post study chat. Ownership is checked first (another user's room → 404), then post visibility
 * (hidden/deleted post or inactive persona → 404).
 *
 * <p>Chat turn: owner check → per-room guard (409 if busy) → read-only snapshot → AI call with no transaction
 * → one write transaction (user + persona message, latest assessments, CHAT_MISSED review items,
 * last_message_at). Any AI failure stores nothing.
 */
@Service
public class StudyConversationService {

    private static final int AI_MODEL_MAX = 100;

    private final StudyConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final LearningAssessmentRepository assessmentRepository;
    private final StudyReviewItemCommands reviewItemCommands;
    private final KeyPointRepository keyPointRepository;
    private final VisibilityService visibilityService;
    private final StudyContextLoader contextLoader;
    private final StudyChatAi chatAi;
    private final ConversationLocks locks;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public StudyConversationService(StudyConversationRepository conversationRepository,
                                    ConversationMessageRepository messageRepository,
                                    LearningAssessmentRepository assessmentRepository,
                                    StudyReviewItemCommands reviewItemCommands,
                                    KeyPointRepository keyPointRepository,
                                    VisibilityService visibilityService,
                                    StudyContextLoader contextLoader,
                                    StudyChatAi chatAi,
                                    ConversationLocks locks,
                                    PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.assessmentRepository = assessmentRepository;
        this.reviewItemCommands = reviewItemCommands;
        this.keyPointRepository = keyPointRepository;
        this.visibilityService = visibilityService;
        this.contextLoader = contextLoader;
        this.chatAi = chatAi;
        this.locks = locks;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** Result of {@link #open}: {@code created} decides 201 vs 200. */
    public record OpenResult(boolean created, StudyConversationResponse conversation) {
    }

    @Transactional
    public OpenResult open(Long postId, AuthUser user) {
        Post post = visibilityService.getVisiblePost(postId);
        StudyConversation existing = conversationRepository.findByUserIdAndPostId(user.id(), post.getId()).orElse(null);
        if (existing != null) {
            return new OpenResult(false, toResponse(existing, post, null, Cursors.DEFAULT_LIMIT));
        }
        StudyConversation created = conversationRepository.save(new StudyConversation(user.id(), post.getId()));
        return new OpenResult(true, toResponse(created, post, null, Cursors.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public StudyConversationResponse get(Long conversationId, AuthUser user, Long beforeMessageId, Integer limit) {
        int pageSize = Cursors.limit(limit);
        StudyConversation conversation = ownedConversation(conversationId, user);
        Post post = visibilityService.getVisiblePost(conversation.getPostId());
        return toResponse(conversation, post, beforeMessageId, pageSize);
    }

    @Transactional(readOnly = true)
    public AssessmentResponse assessment(Long conversationId, AuthUser user) {
        StudyConversation conversation = ownedConversation(conversationId, user);
        visibilityService.requireVisiblePost(conversation.getPostId());
        Map<Long, LearningAssessment> latest = assessmentRepository.findByConversationId(conversation.getId())
                .stream()
                .collect(Collectors.toMap(LearningAssessment::getKeyPointId, Function.identity()));
        List<AssessmentResponse.Item> items = keyPointRepository
                .findByPostIdOrderByPointOrderAsc(conversation.getPostId()).stream()
                .map(point -> {
                    LearningAssessment a = latest.get(point.getId());
                    return new AssessmentResponse.Item(Ids.str(point.getId()), point.getPointOrder(), point.getText(),
                            a == null ? null : a.getLevel().name(),
                            a == null ? null : a.getFeedback(),
                            a == null ? null : Ids.str(a.getEvidenceMessageId()),
                            a == null ? null : a.getUpdatedAt());
                })
                .toList();
        return new AssessmentResponse(Ids.str(conversation.getId()), Ids.str(conversation.getPostId()), items);
    }

    /** One chat turn. Not transactional: the AI call runs outside any DB transaction. */
    public SendMessageResponse sendMessage(Long conversationId, AuthUser user, String rawContent) {
        String content = rawContent.strip();
        Long userId = user.id();
        readTx.executeWithoutResult(status -> ownedConversation(conversationId, user));

        locks.acquire(conversationId);
        try {
            Prepared prepared = readTx.execute(status -> prepare(conversationId, user));
            StudyChatAi.Reply reply = chatAi.reply(prepared.context(), prepared.history(), content);
            return writeTx.execute(status -> persist(conversationId, userId, content, reply));
        } finally {
            locks.release(conversationId);
        }
    }

    private record Prepared(StudyPostContext context, List<StudyChatAi.Turn> history) {
    }

    private Prepared prepare(Long conversationId, AuthUser user) {
        StudyConversation conversation = ownedConversation(conversationId, user);
        StudyPostContext context = contextLoader.load(conversation.getPostId());
        List<ConversationMessage> recent = new ArrayList<>(messageRepository.findByConversationIdOrderByIdDesc(
                conversation.getId(), PageRequest.ofSize(StudyChatAi.HISTORY_LIMIT)));
        Collections.reverse(recent);
        List<StudyChatAi.Turn> history = recent.stream()
                .map(m -> new StudyChatAi.Turn(m.getRole() == MessageRole.USER, m.getContent()))
                .toList();
        return new Prepared(context, history);
    }

    private SendMessageResponse persist(Long conversationId, Long userId, String content, StudyChatAi.Reply reply) {
        StudyConversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(ApiException::notFound);
        Long postId = conversation.getPostId();
        visibilityService.requireVisiblePost(postId);

        ConversationMessage userMessage = messageRepository.save(
                new ConversationMessage(conversation.getId(), MessageRole.USER, content));
        ConversationMessage personaMessage = new ConversationMessage(conversation.getId(), MessageRole.PERSONA,
                reply.reply());
        personaMessage.setAiModel(truncate(reply.aiModel(), AI_MODEL_MAX));
        personaMessage = messageRepository.save(personaMessage);

        List<SendMessageResponse.AssessmentResult> results = new ArrayList<>();
        for (StudyChatAi.Assessment item : reply.assessments()) {
            LearningAssessment assessment = assessmentRepository
                    .findById(new LearningAssessmentId(conversation.getId(), item.keyPointId()))
                    .orElse(null);
            if (assessment == null) {
                assessment = new LearningAssessment(conversation.getId(), item.keyPointId(), postId,
                        userMessage.getId(), item.level(), item.feedback());
            } else {
                assessment.setEvidenceMessageId(userMessage.getId());
                assessment.setLevel(item.level());
                assessment.setFeedback(item.feedback());
            }
            assessmentRepository.save(assessment);
            if (item.level() == AssessmentLevel.MISSED) {
                reviewItemCommands.upsertOpen(userId, postId, item.keyPointId(), ReviewReason.CHAT_MISSED.name());
            }
            results.add(new SendMessageResponse.AssessmentResult(Ids.str(item.keyPointId()), item.level().name(),
                    item.feedback(), Ids.str(userMessage.getId())));
        }

        conversation.setLastMessageAt(personaMessage.getCreatedAt());
        conversationRepository.saveAndFlush(conversation);

        return new SendMessageResponse(StudyMessageResponse.from(userMessage),
                StudyMessageResponse.from(personaMessage), results);
    }

    private StudyConversation ownedConversation(Long conversationId, AuthUser user) {
        return conversationRepository.findByIdAndUserId(conversationId, user.id())
                .orElseThrow(ApiException::notFound);
    }

    private StudyConversationResponse toResponse(StudyConversation conversation, Post post, Long beforeMessageId,
                                                 int limit) {
        PageRequest page = PageRequest.ofSize(limit + 1);
        List<ConversationMessage> rows = beforeMessageId == null
                ? messageRepository.findByConversationIdOrderByIdDesc(conversation.getId(), page)
                : messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(conversation.getId(),
                        beforeMessageId, page);
        boolean hasMore = rows.size() > limit;
        List<ConversationMessage> pageRows = new ArrayList<>(hasMore ? rows.subList(0, limit) : rows);
        Collections.reverse(pageRows);
        String nextBefore = hasMore && !pageRows.isEmpty() ? Ids.str(pageRows.get(0).getId()) : null;

        Persona persona = post.getPersona();
        List<KeyPoint> keyPoints = keyPointRepository.findByPostIdOrderByPointOrderAsc(post.getId());
        return new StudyConversationResponse(
                Ids.str(conversation.getId()),
                Ids.str(post.getId()),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                new StudyConversationResponse.PostSummary(Ids.str(post.getId()), post.getTitle(), post.getSummary()),
                new StudyConversationResponse.PersonaSummary(Ids.str(persona.getId()), persona.getUsername(),
                        persona.getDisplayName(), persona.getEra(), persona.getAvatarPath()),
                persona.getAiDisclosure(),
                keyPoints.stream()
                        .map(k -> new StudyConversationResponse.KeyPointSummary(Ids.str(k.getId()),
                                k.getPointOrder(), k.getText()))
                        .toList(),
                pageRows.stream().map(StudyMessageResponse::from).toList(),
                hasMore,
                nextBefore);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
