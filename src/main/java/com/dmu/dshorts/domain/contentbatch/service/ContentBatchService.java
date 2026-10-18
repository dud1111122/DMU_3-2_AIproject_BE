package com.dmu.dshorts.domain.contentbatch.service;

import com.dmu.dshorts.domain.contentbatch.dto.ContentBatchRequest;
import com.dmu.dshorts.domain.contentbatch.entity.ContentBatch;
import com.dmu.dshorts.domain.contentbatch.repository.ContentBatchRepository;
import com.dmu.dshorts.domain.generation.service.AiClient;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentBatchService {
    private final ContentBatchRepository batches;private final TopicRepository topics;private final PersonaRepository personas;private final AiClient ai;private final ObjectMapper mapper;private final CatalogService catalog;private final ContentGenerationWorker worker;
    public ContentBatchService(ContentBatchRepository batches,TopicRepository topics,PersonaRepository personas,AiClient ai,ObjectMapper mapper,CatalogService catalog,ContentGenerationWorker worker){this.batches=batches;this.topics=topics;this.personas=personas;this.ai=ai;this.mapper=mapper;this.catalog=catalog;this.worker=worker;}
    public Map<String,Object> create(String user,ContentBatchRequest request){
        request.topicIds().stream().distinct().forEach(id->{if(!topics.existsById(id))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"알 수 없는 주제입니다: "+id);});
        String personaId=request.personaId();if(personaId==null)personaId=personas.findAll().stream().findFirst().orElseThrow().getId();
        if(!personas.existsById(personaId))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페르소나를 찾을 수 없습니다.");
        if(!ai.configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI가 설정되지 않았습니다. AI_PROVIDER=openai, AI_API_KEY, AI_MODEL을 설정해주세요.");
        String topicIds;try{topicIds=mapper.writeValueAsString(request.topicIds().stream().distinct().toList());}catch(Exception e){throw new IllegalStateException(e);}
        ContentBatch batch=batches.save(new ContentBatch(user,personaId,topicIds));worker.generate(batch.getId());return state(batch,user);
    }
    public Map<String,Object> get(String id,String user){ContentBatch b=batches.findById(id).filter(x->x.getUserId().equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"콘텐츠 묶음을 찾을 수 없습니다."));return state(b,user);}
    private Map<String,Object> state(ContentBatch b,String user){Map<String,Object> m=new LinkedHashMap<>();m.put("batchId",b.getId());m.put("status",b.getStatus());m.put("personaId",b.getPersonaId());try{m.put("topicIds",mapper.readValue(b.getTopicIds(),mapper.getTypeFactory().constructCollectionType(List.class,String.class)));}catch(Exception ignored){m.put("topicIds",List.of());}m.put("createdAt",b.getCreatedAt());m.put("updatedAt",b.getUpdatedAt());if(b.getErrorMessage()!=null)m.put("errorMessage",b.getErrorMessage());if(b.getPostId()!=null)m.put("posts",List.of(catalog.post(b.getPostId(),user)));return m;}
}
