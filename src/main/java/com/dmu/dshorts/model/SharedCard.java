package com.dmu.dshorts.model;
import jakarta.persistence.*;
@Entity @Table(name="shared_cards")
public class SharedCard {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=80) private String topic;
 @Column(nullable=false,length=180) private String title;
 @Column(nullable=false,columnDefinition="TEXT") private String content;
 @Column(nullable=false,length=160) private String source;
 @Column(length=1000) private String sourceUrl;
 @Column(nullable=false) private boolean published=true;
 @Column(nullable=false) private boolean hidden=false;
 protected SharedCard(){} public SharedCard(String t,String ti,String c,String s,String u){topic=t;title=ti;content=c;source=s;sourceUrl=u;}
 public Long getId(){return id;} public String getTopic(){return topic;} public String getTitle(){return title;} public String getContent(){return content;} public String getSource(){return source;} public String getSourceUrl(){return sourceUrl;} public boolean isPublished(){return published;} public boolean isHidden(){return hidden;} public void hide(){hidden=true;published=false;}
}
