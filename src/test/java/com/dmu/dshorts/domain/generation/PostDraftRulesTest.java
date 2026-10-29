package com.dmu.dshorts.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.service.AiPostParser;
import com.dmu.dshorts.domain.generation.service.InvalidPostDraftException;
import com.dmu.dshorts.domain.generation.service.PostDraftValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** AI output parsing + content rules (no Spring). */
class PostDraftRulesTest {

    private static final Set<String> ALLOWED = Set.of("철학", "삶·자기이해");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PostSeed parse(Map<String, Object> raw) {
        JsonNode node = objectMapper.valueToTree(raw);
        return AiPostParser.parse(node, "nietzsche", "철학");
    }

    private void assertRejected(Consumer<Map<String, Object>> breaker, String messagePart) {
        Map<String, Object> raw = GenerationFixtures.validAiPost();
        breaker.accept(raw);
        assertThatThrownBy(() -> PostDraftValidator.validate(parse(raw), ALLOWED))
                .isInstanceOf(InvalidPostDraftException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    void validOutputPassesAndIsNormalised() {
        PostSeed draft = parse(GenerationFixtures.validAiPost());
        assertThatCode(() -> PostDraftValidator.validate(draft, ALLOWED)).doesNotThrowAnyException();
        assertThat(draft.topics()).containsExactly("철학");
        assertThat(draft.sources().get(0).url()).as("unparseable URL becomes null").isNull();
        assertThat(draft.sections()).allSatisfy(section -> assertThat(section.imagePath()).isNull());
    }

    @Test
    void sectionRules() {
        assertRejected(raw -> raw.put("sections", List.of()), "sections");
        assertRejected(raw -> {
            List<Map<String, Object>> many = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                many.add(GenerationFixtures.section(null, "본문 " + i, "RECONSTRUCTION", null));
            }
            raw.put("sections", many);
        }, "sections");
        assertRejected(raw -> GenerationFixtures.sections(raw).get(0).put("body", " "), "body required");
        assertRejected(raw -> GenerationFixtures.sections(raw).get(1).put("quoteAttribution", null),
                "DIRECT_QUOTE needs quoteAttribution");
        assertRejected(raw -> GenerationFixtures.sections(raw).get(0).put("citationType", "QUOTE"), "citationType");
    }

    @Test
    void oneSectionIsEnough() {
        Map<String, Object> raw = GenerationFixtures.validAiPost();
        raw.put("sections", new ArrayList<>(List.of(
                GenerationFixtures.section(null, "단 하나의 카드", "RECONSTRUCTION", null))));
        GenerationFixtures.keyPoints(raw).forEach(point -> point.put("sectionOrder", 1));
        assertThatCode(() -> PostDraftValidator.validate(parse(raw), ALLOWED)).doesNotThrowAnyException();
    }

    @Test
    void keyPointAndQuestionRules() {
        assertRejected(raw -> GenerationFixtures.keyPoints(raw).remove(1), "key points");
        assertRejected(raw -> {
            for (int i = 0; i < 3; i++) {
                GenerationFixtures.keyPoints(raw).add(GenerationFixtures.keyPoint("추가 " + i, null, 1));
            }
        }, "key points");
        assertRejected(raw -> GenerationFixtures.keyPoints(raw).get(0).put("sectionOrder", 9), "sectionOrder");
        assertRejected(raw -> GenerationFixtures.firstQuestion(raw).put("options", List.of("a", "b", "c")),
                "exactly 4 options");
        assertRejected(raw -> GenerationFixtures.firstQuestion(raw).put("options", List.of("a", "b", "c", "a")),
                "distinct");
        assertRejected(raw -> GenerationFixtures.firstQuestion(raw).put("correctOptionOrder", 5),
                "correctOptionOrder");
        assertRejected(raw -> GenerationFixtures.firstQuestion(raw).put("correctOptionOrder", 0),
                "correctOptionOrder");
        assertRejected(raw -> GenerationFixtures.keyPoints(raw).get(0).remove("question"), "question");
    }

    @Test
    void topicAndSourceRules() {
        assertRejected(raw -> raw.put("topics", List.of("경제")), "topics");
        assertRejected(raw -> raw.put("sources", List.of()), "sources");
        assertRejected(raw -> raw.put("title", ""), "title");
    }

    @Test
    void wrongJsonTypesAreRejected() {
        Map<String, Object> raw = GenerationFixtures.validAiPost();
        GenerationFixtures.firstQuestion(raw).put("correctOptionOrder", "2");
        assertThatThrownBy(() -> parse(raw)).isInstanceOf(InvalidPostDraftException.class);

        Map<String, Object> noArray = GenerationFixtures.validAiPost();
        noArray.put("sections", "카드");
        assertThatThrownBy(() -> parse(noArray)).isInstanceOf(InvalidPostDraftException.class);

        assertThatThrownBy(() -> AiPostParser.parse(objectMapper.valueToTree(List.of()), "x", "철학"))
                .isInstanceOf(InvalidPostDraftException.class);
    }
}
