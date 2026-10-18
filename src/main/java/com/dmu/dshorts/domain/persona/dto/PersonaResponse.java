package com.dmu.dshorts.domain.persona.dto;
public record PersonaResponse(String id,String name,String handle,String era,String field,String avatarUrl,
    String bio,String aiDisclosure,boolean followed) {}
