package com.dmu.dshorts.domain.study.service;

import com.dmu.dshorts.domain.generation.service.AiClient;
import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.domain.study.dto.StudyMessageRequest;
import com.dmu.dshorts.domain.study.entity.*;
import com.dmu.dshorts.domain.study.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyService {
    private final StudyConversationRepository conversations;private final ConversationMessageRepository messages;private final StudyAssessmentRepository assessments;private final ReviewItemRepository reviews;private final CatalogService catalog;private final PersonaRepository personas;private final AiClient ai;private final ObjectMapper mapper;
    public StudyService(StudyConversationRepository conversations,ConversationMessageRepository messages,StudyAssessmentRepository assessments,ReviewItemRepository reviews,CatalogService catalog,PersonaRepository personas,AiClient ai,ObjectMapper mapper){this.conversations=conversations;this.messages=messages;this.assessments=assessments;this.reviews=reviews;this.catalog=catalog;this.personas=personas;this.ai=ai;this.mapper=mapper;}
    @Transactional public Map<String,Object> create(String user,String postId){Post post=catalog.requirePost(postId);StudyConversation c=conversations.save(new StudyConversation(user,post.getPersona().getId(),postId));return Map.of("conversationId",c.getId(),"personaId",c.getPersonaId(),"postId",postId,"aiDisclosure","AI로 재현한 페르소나이며 실제 인물의 발언이 아닙니다.");}
    @Transactional(readOnly=true) public Map<String,Object> get(String id,String user){StudyConversation c=require(id,user);List<Map<String,Object>> list=messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(this::messageMap).toList();return Map.of("conversationId",id,"personaId",c.getPersonaId(),"postId",c.getPostId(),"aiDisclosure","AI로 재현한 페르소나이며 실제 인물의 발언이 아닙니다.","messages",list);}
    @Transactional public Map<String,Object> send(String id,String user,StudyMessageRequest request){
        StudyConversation c=require(id,user);Post post=catalog.requirePost(c.getPostId());Persona persona=personas.findById(c.getPersonaId()).orElseThrow();
        List<ConversationMessage> existing=messages.findByConversationIdOrderByCreatedAtAsc(id);List<String> history=existing.stream().map(m->m.getRole()+": "+m.getContent()).toList();
        String reply=ai.respond(persona,post,history,request.content());
        var userMessage=messages.save(new ConversationMessage(id,"USER",request.content()));var assistant=messages.save(new ConversationMessage(id,"PERSONA",reply));
        return Map.of("conversationId",id,"messages",List.of(messageMap(userMessage),messageMap(assistant)),"expressionType","RECONSTRUCTION","sources",post.getSources().stream().map(s->Map.of("title",s.getTitle(),"verified",s.isVerified())).toList());
    }
    @Transactional public Map<String,Object> assess(String id,String user){
        StudyConversation c=require(id,user);Post post=catalog.requirePost(c.getPostId());Persona persona=personas.findById(c.getPersonaId()).orElseThrow();List<String> history=messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(m->m.getRole()+": "+m.getContent()).toList();
        if(history.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"평가할 대화가 없습니다.");
        JsonNode result=ai.assess(persona,post,history);try{assessments.save(new StudyAssessment(id,mapper.writeValueAsString(result)));}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"평가를 저장하지 못했습니다.");}
        for(JsonNode point:result.path("points")){String status=point.path("status").asText();if(status.equals("MISSED")||status.equals("PARTIAL")){int order=point.path("keyPointOrder").asInt(-1);if(order>=0&&order<post.getKeyPoints().size()){var item=reviews.findByUserIdAndPostIdAndKeyPointOrder(user,post.getId(),order).orElseGet(()->new ReviewItem(user,post.getId(),order,"CHAT_"+status));reviews.save(item);}}}
        return Map.of("conversationId",id,"assessment",result);
    }
    private StudyConversation require(String id,String user){return conversations.findById(id).filter(c->c.getUserId().equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"대화를 찾을 수 없습니다."));}
    private Map<String,Object> messageMap(ConversationMessage m){return Map.of("id",m.getId(),"role",m.getRole(),"content",m.getContent(),"createdAt",m.getCreatedAt());}
}
