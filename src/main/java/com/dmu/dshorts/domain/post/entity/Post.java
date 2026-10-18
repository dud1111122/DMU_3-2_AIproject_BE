package com.dmu.dshorts.domain.post.entity;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.topic.entity.Topic;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "posts")
public class Post {
    @Id private String id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private Persona persona;
    @ManyToMany(fetch=FetchType.LAZY) @JoinTable(name="post_topics",
        joinColumns=@JoinColumn(name="post_id"), inverseJoinColumns=@JoinColumn(name="topic_id"))
    private List<Topic> topics = new ArrayList<>();
    @Column(nullable=false) private String title;
    @Column(columnDefinition="TEXT") private String summary;
    @Column(columnDefinition="TEXT") private String caption;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private GroundingMode groundingMode;
    @Column(nullable=false) private boolean published=true;
    @Column(nullable=false) private boolean hidden=false;
    private Instant createdAt=Instant.now();
    @ElementCollection @CollectionTable(name="post_sections", joinColumns=@JoinColumn(name="post_id"))
    @OrderColumn(name="section_order") private List<PostSection> sections = new ArrayList<>();
    @ElementCollection @CollectionTable(name="post_key_points", joinColumns=@JoinColumn(name="post_id"))
    @OrderColumn(name="point_order") private List<KeyPoint> keyPoints = new ArrayList<>();
    @ElementCollection @CollectionTable(name="post_sources", joinColumns=@JoinColumn(name="post_id"))
    @OrderColumn(name="source_order") private List<PostSource> sources = new ArrayList<>();
    protected Post() {}
    public Post(String id, Persona persona, List<Topic> topics, String title, String summary, String caption, GroundingMode groundingMode, List<PostSection> sections, List<KeyPoint> keyPoints, List<PostSource> sources) {
        this.id=id == null ? UUID.randomUUID().toString() : id; this.persona=persona; this.topics=new ArrayList<>(topics);
        this.title=title; this.summary=summary; this.caption=caption; this.groundingMode=groundingMode;
        this.sections=new ArrayList<>(sections); this.keyPoints=new ArrayList<>(keyPoints); this.sources=new ArrayList<>(sources);
    }
    public String getId(){return id;} public Persona getPersona(){return persona;} public List<Topic> getTopics(){return topics;} public String getTitle(){return title;} public String getSummary(){return summary;} public String getCaption(){return caption;} public GroundingMode getGroundingMode(){return groundingMode;} public boolean isPublished(){return published;} public boolean isHidden(){return hidden;} public Instant getCreatedAt(){return createdAt;} public List<PostSection> getSections(){return sections;} public List<KeyPoint> getKeyPoints(){return keyPoints;} public List<PostSource> getSources(){return sources;}
    public void hide(){this.hidden=true;}
}
