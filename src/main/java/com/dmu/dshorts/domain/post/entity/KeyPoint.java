package com.dmu.dshorts.domain.post.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class KeyPoint {
    private String text;
    private Integer sectionOrder;
    protected KeyPoint() {}
    public KeyPoint(String text,Integer sectionOrder){this.text=text;this.sectionOrder=sectionOrder;}
    public String getText(){return text;} public Integer getSectionOrder(){return sectionOrder;}
}
