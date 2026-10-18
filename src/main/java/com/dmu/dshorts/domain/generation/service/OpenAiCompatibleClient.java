package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class OpenAiCompatibleClient implements AiClient {
    private final RestClient client;private final ObjectMapper mapper;
    @Value("${app.ai.provider:}") private String provider;
    @Value("${app.ai.api-key:}") private String apiKey;
    @Value("${app.ai.model:}") private String model;
    public OpenAiCompatibleClient(RestClient.Builder builder,ObjectMapper mapper){this.client=builder.baseUrl("https://api.openai.com/v1").build();this.mapper=mapper;}
    @Override public boolean configured(){return "openai".equalsIgnoreCase(provider)&&apiKey!=null&&!apiKey.isBlank()&&model!=null&&!model.isBlank();}
    @Override public JsonNode generatePost(Persona p,List<Topic> topics){
        String prompt="Create an educational post in JSON with keys title, summary, caption, sections, keyPoints, references, quiz. sections must have 3-5 objects {title,body,mediaType,altText,expressionType}; keyPoints must have 2-4 concise strings; quiz.questions must have one question per keyPoint with {prompt,keyPointOrder,options,correctOption,explanation}; references must have {title,publisher} only, no invented URLs. expressionType is DIRECT_QUOTE only for a verified exact quote, otherwise RECONSTRUCTION. Do not claim the historical person is literally speaking.\nPersona: "+p.getName()+". Perspective: "+p.getPerspective()+". Voice: "+p.getVoice()+". Knowledge scope: "+p.getKnowledgeScope()+". Rules: "+p.getRules()+". Topics: "+topics.stream().map(Topic::getName).toList();
        JsonNode result=complete(prompt,true);
        try{return mapper.readTree(result.asText());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 응답이 JSON 형식이 아닙니다.");}
    }
    @Override public String respond(Persona p,Post post,List<String> history,String userMessage){
        String prompt="You are an AI recreation of "+p.getName()+", clearly not the real person. Follow these rules: "+p.getRules()+". Keep answers grounded in this post and its key points: "+post.getTitle()+" / "+post.getKeyPoints().stream().map(k->k.getText()).toList()+". If uncertain, say so. Mark modern-topic speculation as a reconstruction. Conversation so far: "+String.join("\n",history)+"\nLearner: "+userMessage;
        return complete(prompt,false).asText();
    }
    @Override public JsonNode assess(Persona p,Post post,List<String> conversation){
        String prompt="Assess the learner's understanding of this post. Return JSON {summary,points:[{keyPointOrder,status,feedback,nextQuestion}]} where status is UNDERSTOOD, PARTIAL, or MISSED. Use key points: "+post.getKeyPoints().stream().map(k->k.getText()).toList()+". Conversation: "+String.join("\n",conversation);
        JsonNode result=complete(prompt,true);try{return mapper.readTree(result.asText());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 평가 응답이 JSON 형식이 아닙니다.");}
    }
    private JsonNode complete(String prompt,boolean json){
        if(!configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI가 설정되지 않았습니다. AI_PROVIDER=openai, AI_API_KEY, AI_MODEL을 설정해주세요.");
        Map<String,Object> body=new LinkedHashMap<>();body.put("model",model);body.put("messages",List.of(Map.of("role","system","content","You are a careful educational assistant."),Map.of("role","user","content",prompt)));body.put("temperature",0.4);
        if(json)body.put("response_format",Map.of("type","json_object"));
        try{
            JsonNode response=client.post().uri("/chat/completions").header(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            JsonNode content=response==null?null:response.at("/choices/0/message/content");
            if(content==null||content.isMissingNode()||content.asText().isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 응답이 비어 있습니다.");
            return content;
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 제공자 요청에 실패했습니다.");}
    }
}
