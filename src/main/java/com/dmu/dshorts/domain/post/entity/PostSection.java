package com.dmu.dshorts.domain.post.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class PostSection {
    private String title;
    private String body;
    private String mediaType;
    private String mediaUrl;
    private String altText;
    private String expressionType;
    protected PostSection() {}
    public PostSection(String title,String body,String mediaType,String mediaUrl,String altText,String expressionType){this.title=title;this.body=body;this.mediaType=mediaType;this.mediaUrl=mediaUrl;this.altText=altText;this.expressionType=expressionType;}
    public String getTitle(){return title;} public String getBody(){return body;} public String getMediaType(){return mediaType;} public String getMediaUrl(){return mediaUrl;} public String getAltText(){return altText;} public String getExpressionType(){return expressionType;}
}
