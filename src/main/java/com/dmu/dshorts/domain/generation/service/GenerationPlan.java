package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Fixed subjects for the preparation jobs ({@code classpath:seed/generation-plan.json}, same as 시연_콘텐츠.md §3):
 * 3 slots per persona, each with a subject and a representative topic. The plan is only an input: a planned topic
 * that is no longer one of the persona's active topics, or a persona without a plan (added by an admin), falls back
 * to the persona's active topics in display order and a generic subject.
 */
@Component
public class GenerationPlan {

    static final String PLAN_PATH = "seed/generation-plan.json";

    record PlanFile(int slotsPerPersona, Map<String, List<PlanEntry>> personas) {
    }

    record PlanEntry(String subject, String topic) {
    }

    /** One job to create: slot 1..N, the representative topic and the subject (both fixed at job creation). */
    public record PlannedJob(int slot, Topic topic, String subject) {
    }

    private final PlanFile plan;

    public GenerationPlan(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(PLAN_PATH).getInputStream()) {
            this.plan = objectMapper.readValue(in, PlanFile.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + PLAN_PATH, ex);
        }
    }

    public int slotsPerPersona() {
        return plan.slotsPerPersona();
    }

    /**
     * @param activeTopics the persona's non-deleted topics, ordered by sortOrder, id; must not be empty
     */
    public List<PlannedJob> planFor(Persona persona, List<Topic> activeTopics) {
        if (activeTopics.isEmpty()) {
            throw new IllegalArgumentException("persona has no active topic: " + persona.getUsername());
        }
        List<PlanEntry> entries = plan.personas().getOrDefault(persona.getUsername(), List.of());
        List<PlannedJob> jobs = new ArrayList<>();
        for (int slot = 1; slot <= plan.slotsPerPersona(); slot++) {
            PlanEntry entry = slot <= entries.size() ? entries.get(slot - 1) : null;
            Topic planned = entry == null ? null : activeTopics.stream()
                    .filter(topic -> topic.getName().equals(entry.topic()))
                    .findFirst().orElse(null);
            if (planned != null) {
                jobs.add(new PlannedJob(slot, planned, entry.subject()));
            } else {
                Topic topic = activeTopics.get((slot - 1) % activeTopics.size());
                String subject = topic.getName() + " 입문: " + persona.getDisplayName() + "의 관점으로 묻기 " + slot;
                jobs.add(new PlannedJob(slot, topic, subject));
            }
        }
        return jobs;
    }
}
