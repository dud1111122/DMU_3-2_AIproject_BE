package com.dmu.dshorts.domain.persona.entity;

import com.dmu.dshorts.domain.topic.entity.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Table {@code personas}. AI persona profile plus its current AI settings (perspective/voice/knowledgeScope/prohibitions/
 * conversationRules are server/admin only; never put them in public DTOs).
 * {@code topics} maps the {@code persona_topics} link table and may include soft deleted topics:
 * filter with {@code VisibilityService.publicTopics(...)} for public responses.
 */
@Entity
@Table(name = "personas")
public class Persona {

    public static final String DEFAULT_AI_DISCLOSURE = "실제 인물이 아닌 AI 재현입니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 30)
    private String username;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "era", length = 100)
    private String era;

    @Column(name = "bio", nullable = false, length = 1000)
    private String bio = "";

    @Column(name = "avatar_path")
    private String avatarPath;

    @Column(name = "ai_disclosure", nullable = false)
    private String aiDisclosure = DEFAULT_AI_DISCLOSURE;

    @Column(name = "perspective", nullable = false)
    private String perspective = "";

    @Column(name = "voice", nullable = false)
    private String voice = "";

    @Column(name = "knowledge_scope", nullable = false)
    private String knowledgeScope = "";

    @Column(name = "prohibitions", nullable = false)
    private String prohibitions = "";

    @Column(name = "conversation_rules", nullable = false)
    private String conversationRules = "";

    /** Card-level style for generated posts: hook, structure, tone details. Admin/server only. */
    @Column(name = "post_style", nullable = false)
    private String postStyle = "";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "persona_topics",
            joinColumns = @JoinColumn(name = "persona_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id"))
    private Set<Topic> topics = new HashSet<>();

    protected Persona() {
    }

    public Persona(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEra() {
        return era;
    }

    public void setEra(String era) {
        this.era = era;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getAiDisclosure() {
        return aiDisclosure;
    }

    public void setAiDisclosure(String aiDisclosure) {
        this.aiDisclosure = aiDisclosure;
    }

    public String getPerspective() {
        return perspective;
    }

    public void setPerspective(String perspective) {
        this.perspective = perspective;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getKnowledgeScope() {
        return knowledgeScope;
    }

    public void setKnowledgeScope(String knowledgeScope) {
        this.knowledgeScope = knowledgeScope;
    }

    public String getProhibitions() {
        return prohibitions;
    }

    public void setProhibitions(String prohibitions) {
        this.prohibitions = prohibitions;
    }

    public String getConversationRules() {
        return conversationRules;
    }

    public String getPostStyle() {
        return postStyle;
    }

    public void setPostStyle(String postStyle) {
        this.postStyle = postStyle;
    }

    public void setConversationRules(String conversationRules) {
        this.conversationRules = conversationRules;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<Topic> getTopics() {
        return topics;
    }

    /** Active and not deleted. Public endpoints must only expose visible personas. */
    public boolean isVisible() {
        return active && deletedAt == null;
    }
}
