package com.dmu.dshorts.domain.admin.dto;

import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.AdminTopicRef;
import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** DTOs of {@code /api/v1/admin/posts}. */
public final class AdminPostDtos {

    private AdminPostDtos() {
    }

    public record AdminPersonaRef(String id, String username, String displayName, boolean active, boolean deleted) {

        public static AdminPersonaRef from(Persona p) {
            return new AdminPersonaRef(Ids.str(p.getId()), p.getUsername(), p.getDisplayName(), p.isActive(),
                    p.getDeletedAt() != null);
        }
    }

    /** List row. {@code visible} = what a normal user would see (published, not deleted, persona visible). */
    public record AdminPostSummary(String id, String title, String status, boolean deleted, boolean visible,
                                   Instant deletedAt, Instant publishedAt, Instant updatedAt,
                                   AdminPersonaRef persona) {

        public static AdminPostSummary from(Post p) {
            return new AdminPostSummary(Ids.str(p.getId()), p.getTitle(), p.getStatus().name(), p.isDeleted(),
                    isVisible(p), p.getDeletedAt(), p.getPublishedAt(), p.getUpdatedAt(),
                    AdminPersonaRef.from(p.getPersona()));
        }
    }

    static boolean isVisible(Post p) {
        return p.getStatus() == com.dmu.dshorts.domain.post.entity.PostStatus.PUBLISHED && !p.isDeleted()
                && p.getPersona().isVisible();
    }

    public record AdminSectionImage(String path, String url, String altText, String mime, Integer width,
                                    Integer height, boolean aiGenerated, String credit) {
    }

    public record AdminSection(String id, int order, String title, String body, String citationType,
                               String quoteAttribution, AdminSectionImage image) {

        public static AdminSection from(PostSection s) {
            AdminSectionImage image = s.getImagePath() == null ? null
                    : new AdminSectionImage(s.getImagePath(), MediaStorage.urlOf(s.getImagePath()), s.getImageAlt(),
                    s.getImageMime(), s.getImageWidth(), s.getImageHeight(), s.isImageAiGenerated(),
                    s.getImageCredit());
            return new AdminSection(Ids.str(s.getId()), s.getSectionOrder(), s.getTitle(), s.getBody(),
                    s.getCitationType().name(), s.getQuoteAttribution(), image);
        }
    }

    public record AdminKeyPoint(String id, int order, String text, String sectionId) {

        public static AdminKeyPoint from(KeyPoint k) {
            return new AdminKeyPoint(Ids.str(k.getId()), k.getPointOrder(), k.getText(), Ids.str(k.getSectionId()));
        }
    }

    public record AdminSource(String id, int order, String title, String author, String publisher, String url,
                              String publicationLabel, boolean verified, LocalDate checkedAt, String note) {

        public static AdminSource from(PostSource s) {
            return new AdminSource(Ids.str(s.getId()), s.getSourceOrder(), s.getTitle(), s.getAuthor(),
                    s.getPublisher(), s.getUrl(), s.getPublicationLabel(), s.isVerified(), s.getCheckedAt(),
                    s.getNote());
        }
    }

    public record AdminOption(int order, String text) {

        public static AdminOption from(AnswerOption o) {
            return new AdminOption(o.getOptionOrder(), o.getText());
        }
    }

    /** Read-only quiz view (answers included) for admins. */
    public record AdminQuestion(String id, String keyPointId, String prompt, String explanation,
                                int correctOptionOrder, List<AdminOption> options) {

        public static AdminQuestion from(Question q, List<AnswerOption> options) {
            return new AdminQuestion(Ids.str(q.getId()), Ids.str(q.getKeyPointId()), q.getPrompt(),
                    q.getExplanation(), q.getCorrectOptionOrder(), options.stream().map(AdminOption::from).toList());
        }
    }

    public record AdminPostDetail(String id, AdminPersonaRef persona, String title, String summary, String caption,
                                  String groundingMode, String status, boolean deleted, boolean visible,
                                  Instant deletedAt, String aiModel, Instant publishedAt, Instant updatedAt,
                                  List<AdminTopicRef> topics, List<AdminSection> sections,
                                  List<AdminKeyPoint> keyPoints, List<AdminSource> sources,
                                  List<AdminQuestion> questions) {

        public static AdminPostDetail of(Post p, List<AdminTopicRef> topics, List<AdminSection> sections,
                                         List<AdminKeyPoint> keyPoints, List<AdminSource> sources,
                                         List<AdminQuestion> questions) {
            return new AdminPostDetail(Ids.str(p.getId()), AdminPersonaRef.from(p.getPersona()), p.getTitle(),
                    p.getSummary(), p.getCaption(), p.getGroundingMode().name(), p.getStatus().name(),
                    p.isDeleted(), isVisible(p), p.getDeletedAt(), p.getAiModel(), p.getPublishedAt(),
                    p.getUpdatedAt(), topics, sections, keyPoints, sources, questions);
        }
    }

    /**
     * {@code PATCH /admin/posts/{id}}. Every field is optional ({@code null}/absent = unchanged).
     * <ul>
     *   <li>{@code sections}: edits of <b>existing</b> cards by {@code id}; omitted cards stay unchanged. Adding,
     *       removing or reordering cards → 409.</li>
     *   <li>{@code sources}: item with {@code id} edits that source ({@code delete:true} removes it); item without
     *       {@code id} appends a new source.</li>
     *   <li>{@code keyPoints}, {@code questions}, {@code quiz}: learning structure is fixed after publishing → 409.</li>
     * </ul>
     */
    public record UpdatePostRequest(String title, String summary, String caption, List<SectionPatch> sections,
                                    List<SourcePatch> sources, JsonNode keyPoints, JsonNode questions,
                                    JsonNode quiz) {
    }

    /**
     * Card edit. {@code title}/{@code quoteAttribution}: {@code ""} clears. {@code image}: absent = unchanged,
     * {@code null} = remove, object = set ({@code {path, altText, aiGenerated?, credit?}}; path from the upload API).
     * {@code order} may be sent but must equal the current order.
     */
    public record SectionPatch(String id, Integer order, String title, String body, String citationType,
                               String quoteAttribution, JsonNode image, Boolean delete) {
    }

    public record SectionImageInput(String path, String altText, Boolean aiGenerated, String credit) {
    }

    /** Source edit/add. Optional strings: {@code ""} clears. {@code checkedAt}: ISO date, {@code ""} clears. */
    public record SourcePatch(String id, String title, String author, String publisher, String url,
                              String publicationLabel, Boolean verified, String checkedAt, String note,
                              Boolean delete) {
    }

    public record VisibilityRequest(String status) {
    }
}
