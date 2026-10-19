package com.dmu.dshorts.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Table {@code post_sources}. Bibliography entry of a post. AI-suggested sources are {@code verified=false}; verified requires checkedAt.
 */
@Entity
@Table(name = "post_sources")
public class PostSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "source_order", nullable = false)
    private short sourceOrder;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "author", length = 200)
    private String author;

    @Column(name = "publisher", length = 200)
    private String publisher;

    @Column(name = "url")
    private String url;

    @Column(name = "publication_label", length = 50)
    private String publicationLabel;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "checked_at")
    private LocalDate checkedAt;

    @Column(name = "note")
    private String note;

    protected PostSource() {
    }

    public PostSource(Long postId, short sourceOrder, String title) {
        this.postId = postId;
        this.sourceOrder = sourceOrder;
        this.title = title;
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

    public short getSourceOrder() {
        return sourceOrder;
    }

    public void setSourceOrder(short sourceOrder) {
        this.sourceOrder = sourceOrder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPublicationLabel() {
        return publicationLabel;
    }

    public void setPublicationLabel(String publicationLabel) {
        this.publicationLabel = publicationLabel;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public LocalDate getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDate checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
