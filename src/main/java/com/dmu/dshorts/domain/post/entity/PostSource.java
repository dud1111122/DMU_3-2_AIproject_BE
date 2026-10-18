package com.dmu.dshorts.domain.post.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class PostSource {
    private String title;
    private String publisher;
    private String url;
    private String publishedDate;
    private String relationship;
    private boolean verified;
    protected PostSource() {}
    public PostSource(String title,String publisher,String url,String publishedDate,String relationship,boolean verified){this.title=title;this.publisher=publisher;this.url=url;this.publishedDate=publishedDate;this.relationship=relationship;this.verified=verified;}
    public String getTitle(){return title;} public String getPublisher(){return publisher;} public String getUrl(){return url;} public String getPublishedDate(){return publishedDate;} public String getRelationship(){return relationship;} public boolean isVerified(){return verified;}
}
