package com.dmu.dshorts.domain.admin.service;

import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminKeyPoint;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminPostDetail;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminPostSummary;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminQuestion;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminSection;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminSource;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.SectionImageInput;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.SectionPatch;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.SourcePatch;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.UpdatePostRequest;
import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.AdminTopicRef;
import com.dmu.dshorts.domain.admin.media.ImageInspector.ImageInfo;
import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.domain.admin.repository.AdminPostQueries;
import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.post.entity.CitationType;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostSource;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminPostService {

    private final PostRepository postRepository;
    private final AdminPostQueries postQueries;
    private final PostSectionRepository sectionRepository;
    private final KeyPointRepository keyPointRepository;
    private final PostSourceRepository sourceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final MediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    public AdminPostService(PostRepository postRepository, AdminPostQueries postQueries,
                            PostSectionRepository sectionRepository, KeyPointRepository keyPointRepository,
                            PostSourceRepository sourceRepository, QuestionRepository questionRepository,
                            AnswerOptionRepository answerOptionRepository, MediaStorage mediaStorage,
                            ObjectMapper objectMapper) {
        this.postRepository = postRepository;
        this.postQueries = postQueries;
        this.sectionRepository = sectionRepository;
        this.keyPointRepository = keyPointRepository;
        this.sourceRepository = sourceRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPostSummary> list(String statusParam, boolean includeDeleted, String cursorParam,
                                               Integer limitParam) {
        PostStatus status = parseStatus(statusParam, "status", false);
        int limit = Cursors.limit(limitParam);
        List<Post> rows = postQueries.findPage(status, includeDeleted, Cursors.decodeTimeId(cursorParam), limit + 1);
        return PageResponse.slice(rows, limit, p -> Cursors.encode(p.getPublishedAt(), p.getId()),
                AdminPostSummary::from);
    }

    @Transactional(readOnly = true)
    public AdminPostDetail get(Long id) {
        return detail(load(id));
    }

    public AdminPostDetail update(Long id, UpdatePostRequest request) {
        Post post = load(id);
        if (post.isDeleted()) {
            throw ApiException.conflict("삭제된 게시물은 수정할 수 없습니다.");
        }
        if (present(request.keyPoints()) || present(request.questions()) || present(request.quiz())) {
            throw ApiException.conflict("게시 후에는 학습 포인트와 퀴즈를 수정할 수 없습니다. 새 게시물을 준비해주세요.");
        }
        if (request.title() != null) {
            post.setTitle(AdminText.requireText(request.title(), "title", 200));
        }
        if (request.summary() != null) {
            post.setSummary(AdminText.optionalText(request.summary(), "summary", 0, ""));
        }
        if (request.caption() != null) {
            post.setCaption(AdminText.optionalText(request.caption(), "caption", 0, ""));
        }
        if (request.sections() != null) {
            updateSections(post.getId(), request.sections());
        }
        if (request.sources() != null) {
            updateSources(post.getId(), request.sources());
        }
        post.setUpdatedAt(Instant.now());
        postRepository.flush();
        return detail(post);
    }

    public AdminPostSummary changeVisibility(Long id, String statusParam) {
        PostStatus status = parseStatus(statusParam, "status", true);
        Post post = load(id);
        if (post.isDeleted()) {
            throw ApiException.conflict("삭제된 게시물의 노출 상태는 바꿀 수 없습니다.");
        }
        if (post.getStatus() != status) {
            post.setStatus(status);
            post.setUpdatedAt(Instant.now());
            postRepository.flush();
        }
        return AdminPostSummary.from(post);
    }

    /** Logical delete. Idempotent. */
    public void delete(Long id) {
        Post post = postRepository.findById(id).orElseThrow(ApiException::notFound);
        if (!post.isDeleted()) {
            post.setDeletedAt(Instant.now());
            post.setUpdatedAt(Instant.now());
        }
    }

    private Post load(Long id) {
        return postRepository.findWithPersonaById(id).orElseThrow(ApiException::notFound);
    }

    private AdminPostDetail detail(Post post) {
        List<AdminTopicRef> topics = post.getTopics().stream()
                .sorted(Comparator.comparingInt(Topic::getSortOrder).thenComparing(Topic::getId))
                .map(AdminTopicRef::from)
                .toList();
        List<AdminSection> sections = sectionRepository.findByPostIdOrderBySectionOrderAsc(post.getId()).stream()
                .map(AdminSection::from).toList();
        List<AdminKeyPoint> keyPoints = keyPointRepository.findByPostIdOrderByPointOrderAsc(post.getId()).stream()
                .map(AdminKeyPoint::from).toList();
        List<AdminSource> sources = sourceRepository.findByPostIdOrderBySourceOrderAsc(post.getId()).stream()
                .map(AdminSource::from).toList();
        List<Question> questions = questionRepository.findByPostId(post.getId());
        Map<Long, List<AnswerOption>> options = questions.isEmpty() ? Map.of()
                : answerOptionRepository.findByQuestionIdInOrderByQuestionIdAscOptionOrderAsc(
                        questions.stream().map(Question::getId).toList())
                .stream().collect(Collectors.groupingBy(AnswerOption::getQuestionId));
        Map<Long, Integer> pointOrder = keyPoints.stream()
                .collect(Collectors.toMap(k -> Long.valueOf(k.id()), AdminKeyPoint::order));
        List<AdminQuestion> questionDtos = questions.stream()
                .sorted(Comparator.comparing((Question q) -> pointOrder.getOrDefault(q.getKeyPointId(), 0))
                        .thenComparing(Question::getId))
                .map(q -> AdminQuestion.from(q, options.getOrDefault(q.getId(), List.of())))
                .toList();
        return AdminPostDetail.of(post, topics, sections, keyPoints, sources, questionDtos);
    }

    // ---- sections -------------------------------------------------------------------------------------------

    private void updateSections(Long postId, List<SectionPatch> patches) {
        Map<Long, PostSection> existing = sectionRepository.findByPostIdOrderBySectionOrderAsc(postId).stream()
                .collect(Collectors.toMap(PostSection::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < patches.size(); i++) {
            SectionPatch patch = patches.get(i);
            String field = "sections[" + i + "]";
            if (patch == null) {
                throw ApiException.validation(field, "필수 값입니다.");
            }
            if (patch.id() == null || Boolean.TRUE.equals(patch.delete())) {
                throw structureChange();
            }
            Long sectionId = Ids.parseBodyId(patch.id(), field + ".id");
            PostSection section = existing.get(sectionId);
            if (section == null) {
                throw structureChange();
            }
            if (!seen.add(sectionId)) {
                throw ApiException.validation(field + ".id", "같은 카드를 두 번 수정할 수 없습니다.");
            }
            if (patch.order() != null && patch.order() != section.getSectionOrder()) {
                throw structureChange();
            }
            applySection(section, patch, field);
        }
    }

    private void applySection(PostSection section, SectionPatch patch, String field) {
        if (patch.title() != null) {
            section.setTitle(AdminText.optionalText(patch.title(), field + ".title", 200, null));
        }
        if (patch.body() != null) {
            section.setBody(AdminText.requireText(patch.body(), field + ".body", 0));
        }
        if (patch.citationType() != null) {
            try {
                section.setCitationType(CitationType.valueOf(patch.citationType().trim()));
            } catch (IllegalArgumentException ex) {
                throw ApiException.validation(field + ".citationType", "DIRECT_QUOTE 또는 RECONSTRUCTION이어야 합니다.");
            }
        }
        if (patch.quoteAttribution() != null) {
            section.setQuoteAttribution(AdminText.optionalText(patch.quoteAttribution(),
                    field + ".quoteAttribution", 0, null));
        }
        if (section.getCitationType() == CitationType.DIRECT_QUOTE && section.getQuoteAttribution() == null) {
            throw ApiException.validation(field + ".quoteAttribution", "직접 인용 카드에는 출처 표시가 필요합니다.");
        }
        if (patch.image() != null) {
            if (patch.image().isNull()) {
                clearImage(section);
            } else {
                applyImage(section, readImage(patch.image(), field + ".image"), field + ".image");
            }
        }
    }

    private SectionImageInput readImage(JsonNode node, String field) {
        if (!node.isObject()) {
            throw ApiException.validation(field, "이미지 객체 또는 null이어야 합니다.");
        }
        try {
            return objectMapper.treeToValue(node, SectionImageInput.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw ApiException.validation(field, "이미지 형식이 올바르지 않습니다.");
        }
    }

    private void applyImage(PostSection section, SectionImageInput input, String field) {
        String path = input.path() == null ? null : input.path().trim();
        if (path == null || path.isEmpty()) {
            throw ApiException.validation(field + ".path", "필수 값입니다.");
        }
        String altText = AdminText.requireText(input.altText(), field + ".altText", 300);
        if (!path.equals(section.getImagePath())) {
            ImageInfo info = mediaStorage.requireExistingImage(path, field + ".path");
            section.setImagePath(path);
            section.setImageMime(info.type().mime());
            section.setImageWidth(info.width());
            section.setImageHeight(info.height());
        }
        section.setImageAlt(altText);
        if (input.aiGenerated() != null) {
            section.setImageAiGenerated(input.aiGenerated());
        }
        if (input.credit() != null) {
            section.setImageCredit(AdminText.optionalText(input.credit(), field + ".credit", 0, null));
        }
    }

    private static void clearImage(PostSection section) {
        section.setImagePath(null);
        section.setImageAlt(null);
        section.setImageMime(null);
        section.setImageWidth(null);
        section.setImageHeight(null);
        section.setImageAiGenerated(false);
        section.setImageCredit(null);
    }

    // ---- sources --------------------------------------------------------------------------------------------

    private void updateSources(Long postId, List<SourcePatch> patches) {
        List<PostSource> current = sourceRepository.findByPostIdOrderBySourceOrderAsc(postId);
        Map<Long, PostSource> existing = current.stream()
                .collect(Collectors.toMap(PostSource::getId, Function.identity()));
        int nextOrder = current.stream().mapToInt(PostSource::getSourceOrder).max().orElse(0) + 1;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < patches.size(); i++) {
            SourcePatch patch = patches.get(i);
            String field = "sources[" + i + "]";
            if (patch == null) {
                throw ApiException.validation(field, "필수 값입니다.");
            }
            PostSource source;
            if (patch.id() == null) {
                if (Boolean.TRUE.equals(patch.delete())) {
                    throw ApiException.validation(field + ".id", "삭제할 문헌의 ID가 필요합니다.");
                }
                if (nextOrder > Short.MAX_VALUE) {
                    throw ApiException.validation(field, "문헌을 더 추가할 수 없습니다.");
                }
                source = new PostSource(postId, (short) nextOrder++,
                        AdminText.requireText(patch.title(), field + ".title", 300));
            } else {
                Long sourceId = Ids.parseBodyId(patch.id(), field + ".id");
                source = existing.get(sourceId);
                if (source == null) {
                    throw ApiException.validation(field + ".id", "이 게시물의 문헌이 아닙니다.");
                }
                if (!seen.add(sourceId)) {
                    throw ApiException.validation(field + ".id", "같은 문헌을 두 번 수정할 수 없습니다.");
                }
                if (Boolean.TRUE.equals(patch.delete())) {
                    sourceRepository.delete(source);
                    continue;
                }
            }
            applySource(source, patch, field);
            sourceRepository.save(source);
        }
    }

    private void applySource(PostSource source, SourcePatch patch, String field) {
        if (patch.title() != null) {
            source.setTitle(AdminText.requireText(patch.title(), field + ".title", 300));
        }
        if (patch.author() != null) {
            source.setAuthor(AdminText.optionalText(patch.author(), field + ".author", 200, null));
        }
        if (patch.publisher() != null) {
            source.setPublisher(AdminText.optionalText(patch.publisher(), field + ".publisher", 200, null));
        }
        if (patch.url() != null) {
            source.setUrl(url(patch.url(), field + ".url"));
        }
        if (patch.publicationLabel() != null) {
            source.setPublicationLabel(AdminText.optionalText(patch.publicationLabel(),
                    field + ".publicationLabel", 50, null));
        }
        if (patch.note() != null) {
            source.setNote(AdminText.optionalText(patch.note(), field + ".note", 0, null));
        }
        if (patch.checkedAt() != null) {
            source.setCheckedAt(date(patch.checkedAt(), field + ".checkedAt"));
        }
        if (patch.verified() != null) {
            source.setVerified(patch.verified());
        }
        if (source.isVerified() && source.getCheckedAt() == null) {
            throw ApiException.validation(field + ".checkedAt", "확인된 문헌에는 확인 날짜가 필요합니다.");
        }
    }

    private static String url(String raw, String field) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        throw ApiException.validation(field, "http(s) URL이어야 합니다.");
    }

    private static LocalDate date(String raw, String field) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.validation(field, "YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private static boolean present(JsonNode node) {
        return node != null && !node.isNull();
    }

    private static ApiException structureChange() {
        return ApiException.conflict("게시 후에는 카드 추가·삭제·순서 변경을 할 수 없습니다. 기존 카드의 내용만 수정할 수 있습니다.");
    }

    static PostStatus parseStatus(String raw, String field, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw ApiException.validation(field, "PUBLISHED 또는 HIDDEN이어야 합니다.");
            }
            return null;
        }
        try {
            return PostStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation(field, "PUBLISHED 또는 HIDDEN이어야 합니다.");
        }
    }
}
