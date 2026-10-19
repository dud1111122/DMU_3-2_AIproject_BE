package com.dmu.dshorts.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code post_sections}. Card of a post ({@code section_order} 1..10, unique per post). DIRECT_QUOTE requires quoteAttribution;
 * imagePath requires a non-empty imageAlt (DB CHECKs).
 */
@Entity
@Table(name = "post_sections")
public class PostSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "section_order", nullable = false)
    private short sectionOrder;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "citation_type", nullable = false)
    private CitationType citationType = CitationType.RECONSTRUCTION;

    @Column(name = "quote_attribution")
    private String quoteAttribution;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "image_alt", length = 300)
    private String imageAlt;

    @Column(name = "image_mime", length = 50)
    private String imageMime;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "image_ai_generated", nullable = false)
    private boolean imageAiGenerated;

    @Column(name = "image_credit")
    private String imageCredit;

    protected PostSection() {
    }

    public PostSection(Long postId, short sectionOrder, String body) {
        this.postId = postId;
        this.sectionOrder = sectionOrder;
        this.body = body;
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

    public short getSectionOrder() {
        return sectionOrder;
    }

    public void setSectionOrder(short sectionOrder) {
        this.sectionOrder = sectionOrder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public CitationType getCitationType() {
        return citationType;
    }

    public void setCitationType(CitationType citationType) {
        this.citationType = citationType;
    }

    public String getQuoteAttribution() {
        return quoteAttribution;
    }

    public void setQuoteAttribution(String quoteAttribution) {
        this.quoteAttribution = quoteAttribution;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImageAlt() {
        return imageAlt;
    }

    public void setImageAlt(String imageAlt) {
        this.imageAlt = imageAlt;
    }

    public String getImageMime() {
        return imageMime;
    }

    public void setImageMime(String imageMime) {
        this.imageMime = imageMime;
    }

    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }

    public boolean isImageAiGenerated() {
        return imageAiGenerated;
    }

    public void setImageAiGenerated(boolean imageAiGenerated) {
        this.imageAiGenerated = imageAiGenerated;
    }

    public String getImageCredit() {
        return imageCredit;
    }

    public void setImageCredit(String imageCredit) {
        this.imageCredit = imageCredit;
    }
}
