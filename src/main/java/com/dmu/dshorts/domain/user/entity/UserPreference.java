package com.dmu.dshorts.domain.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity @Table(name="user_preferences")
public class UserPreference {
    @Id private String userId;
    private String mode="BASIC";
    private Instant updatedAt=Instant.now();
    protected UserPreference() {}
    public UserPreference(String userId){this.userId=userId;}
    public String getUserId(){return userId;} public String getMode(){return mode;} public Instant getUpdatedAt(){return updatedAt;}
    public void setMode(String mode){this.mode=mode;this.updatedAt=Instant.now();}
}
