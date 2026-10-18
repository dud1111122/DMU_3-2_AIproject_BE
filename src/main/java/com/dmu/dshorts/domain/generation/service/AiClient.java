package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
public interface AiClient {
    boolean configured();
    JsonNode generatePost(Persona persona,List<Topic> topics);
    String respond(Persona persona,Post post,List<String> history,String userMessage);
    JsonNode assess(Persona persona,Post post,List<String> conversation);
}
