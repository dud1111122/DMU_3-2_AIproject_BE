package com.dmu.dshorts.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code key_points}. Learning point of a post ({@code point_order} 1..4; a valid post has 2..4). {@code sectionId} optionally
 * points at a card of the same post (composite FK).
 */
@Entity
@Table(name = "key_points")
public class KeyPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "point_order", nullable = false)
    private short pointOrder;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "section_id")
    private Long sectionId;

    protected KeyPoint() {
    }

    public KeyPoint(Long postId, short pointOrder, String text) {
        this.postId = postId;
        this.pointOrder = pointOrder;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public short getPointOrder() {
        return pointOrder;
    }

    public void setPointOrder(short pointOrder) {
        this.pointOrder = pointOrder;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }
}
