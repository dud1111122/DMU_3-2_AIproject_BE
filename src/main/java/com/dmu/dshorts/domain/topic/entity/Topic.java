package com.dmu.dshorts.domain.topic.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "topics")
public class Topic {
    @Id private String id;
    private String name;
    private String description;
    protected Topic() {}
    public Topic(String id, String name, String description) { this.id=id; this.name=name; this.description=description; }
    public String getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
}
