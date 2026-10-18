package com.dmu.dshorts.domain.contentbatch.service;

import com.dmu.dshorts.domain.contentbatch.entity.ContentBatch;
import com.dmu.dshorts.domain.contentbatch.repository.ContentBatchRepository;
import com.dmu.dshorts.domain.generation.service.AiClient;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.*;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.quiz.entity.*;
import com.dmu.dshorts.domain.quiz.repository.QuizRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContentGenerationWorker {
    private final ContentBatchRepository batches;private final TopicRepository topics;private final PersonaRepository personas;private final PostRepository posts;private final QuizRepository quizzes;private final AiClient ai;private final ObjectMapper mapper;
    public ContentGenerationWorker(ContentBatchRepository batches,TopicRepository topics,PersonaRepository personas,PostRepository posts,QuizRepository quizzes,AiClient ai,ObjectMapper mapper){this.batches=batches;this.topics=topics;this.personas=personas;this.posts=posts;this.quizzes=quizzes;this.ai=ai;this.mapper=mapper;}
    @Async public void generate(String batchId){
        Optional<ContentBatch> found=batches.findById(batchId);if(found.isEmpty())return;ContentBatch batch=found.get();batch.generating();batches.save(batch);
        try{
            List<String> topicIds=mapper.readValue(batch.getTopicIds(),mapper.getTypeFactory().constructCollectionType(List.class,String.class));
            List<Topic> selected=topicIds.stream().map(topics::findById).flatMap(Optional::stream).toList();
            Persona persona=personas.findById(batch.getPersonaId()).orElseThrow();JsonNode generated=ai.generatePost(persona,selected);validate(generated);
            List<PostSection> sections=new ArrayList<>();for(JsonNode n:generated.path("sections"))sections.add(new PostSection(s(n,"title"),s(n,"body"),s(n,"mediaType"),s(n,"mediaUrl"),s(n,"altText"),expression(n)));
            List<KeyPoint> points=new ArrayList<>();for(JsonNode n:generated.path("keyPoints"))points.add(new KeyPoint(n.asText(),null));
            List<PostSource> refs=new ArrayList<>();for(JsonNode n:generated.path("references"))refs.add(new PostSource(s(n,"title"),s(n,"publisher"),"","","AI가 생성 과정에서 제시한 참고 문헌; 원문 미확인",false));
            Post post=posts.save(new Post(null,persona,selected,s(generated,"title"),s(generated,"summary"),s(generated,"caption"),GroundingMode.MODEL_KNOWLEDGE,sections,points,refs));
            List<QuizQuestion> questions=new ArrayList<>();for(JsonNode n:generated.path("quiz").path("questions")){List<String> options=new ArrayList<>();n.path("options").forEach(x->options.add(x.asText()));questions.add(new QuizQuestion(s(n,"prompt"),n.path("keyPointOrder").asInt(),n.path("correctOption").asInt(),s(n,"explanation"),options));}
            quizzes.save(new Quiz(UUID.randomUUID().toString(),post,questions));batch.published(post.getId());batches.save(batch);
        }catch(ResponseStatusException ex){fail(batch,ex.getReason());}catch(Exception ex){fail(batch,"생성 결과를 검증하거나 저장하지 못했습니다.");}
    }
    private void validate(JsonNode n){
        if(!n.isObject()||s(n,"title").isBlank()||s(n,"caption").isBlank())throw new IllegalArgumentException("title/caption missing");
        int sectionCount=n.path("sections").size(),pointCount=n.path("keyPoints").size();if(sectionCount<2||sectionCount>8||pointCount<2||pointCount>4)throw new IllegalArgumentException("invalid post length");
        JsonNode questions=n.path("quiz").path("questions");if(questions.size()!=pointCount)throw new IllegalArgumentException("question count must match key points");
        for(JsonNode q:questions){int options=q.path("options").size(),answer=q.path("correctOption").asInt(-1),point=q.path("keyPointOrder").asInt(-1);if(options<2||answer<0||answer>=options||point<0||point>=pointCount)throw new IllegalArgumentException("invalid quiz");}
    }
    private void fail(ContentBatch batch,String reason){batch.failed(reason==null?"AI 생성에 실패했습니다.":reason);batches.save(batch);}
    private static String expression(JsonNode n){String t=s(n,"expressionType");return t.equals("DIRECT_QUOTE")?t:"RECONSTRUCTION";}
    private static String s(JsonNode n,String f){return n.path(f).asText("");}
}
