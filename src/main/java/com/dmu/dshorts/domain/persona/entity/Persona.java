package com.dmu.dshorts.domain.persona.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "personas")
public class Persona {
    @Id private String id;
    private String name;
    private String handle;
    private String era;
    private String field;
    private String avatarUrl;
    private String bio;
    private String perspective;
    private String voice;
    private String knowledgeScope;
    private String rules;
    protected Persona() {}
    public Persona(String id,String name,String handle,String era,String field,String avatarUrl,String bio,String perspective,String voice,String knowledgeScope,String rules){
        this.id=id;this.name=name;this.handle=handle;this.era=era;this.field=field;this.avatarUrl=avatarUrl;this.bio=bio;this.perspective=perspective;this.voice=voice;this.knowledgeScope=knowledgeScope;this.rules=rules;
    }
    public String getId(){return id;} public String getName(){return name;} public String getHandle(){return handle;} public String getEra(){return era;} public String getField(){return field;} public String getAvatarUrl(){return avatarUrl;} public String getBio(){return bio;} public String getPerspective(){return perspective;} public String getVoice(){return voice;} public String getKnowledgeScope(){return knowledgeScope;} public String getRules(){return rules;}
}
