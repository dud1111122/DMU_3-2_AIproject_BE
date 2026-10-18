package com.dmu.dshorts.domain.quiz.service;

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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedDataService implements CommandLineRunner {
    private final ObjectMapper mapper;
    private final TopicRepository topics;
    private final PersonaRepository personas;
    private final PostRepository posts;
    private final QuizRepository quizzes;

    public SeedDataService(ObjectMapper mapper,TopicRepository topics,PersonaRepository personas,PostRepository posts,QuizRepository quizzes){this.mapper=mapper;this.topics=topics;this.personas=personas;this.posts=posts;this.quizzes=quizzes;}

    @Override @Transactional
    public void run(String... args) throws Exception {
        if (topics.count() > 0 || personas.count() > 0 || posts.count() > 0) return;
        try(InputStream input=new ClassPathResource("seed/catalog.json").getInputStream()) {
            JsonNode root=mapper.readTree(input);
            for(JsonNode n:root.path("topics")) topics.save(new Topic(s(n,"id"),s(n,"name"),s(n,"description")));
            for(JsonNode n:root.path("personas")) personas.save(new Persona(s(n,"id"),s(n,"name"),s(n,"handle"),s(n,"era"),s(n,"field"),s(n,"avatarUrl"),s(n,"bio"),s(n,"perspective"),s(n,"voice"),s(n,"knowledgeScope"),s(n,"rules")));
            for(JsonNode n:root.path("posts")) {
                Persona persona=personas.findById(s(n,"personaId")).orElseThrow();
                List<Topic> postTopics=new ArrayList<>(); for(JsonNode t:n.path("topicIds")) topics.findById(t.asText()).ifPresent(postTopics::add);
                List<PostSection> sections=new ArrayList<>(); for(JsonNode x:n.path("sections")) sections.add(new PostSection(s(x,"title"),s(x,"body"),s(x,"mediaType"),s(x,"mediaUrl"),s(x,"altText"),s(x,"expressionType")));
                List<KeyPoint> points=new ArrayList<>(); for(JsonNode x:n.path("keyPoints")) points.add(new KeyPoint(x.asText(),null));
                List<PostSource> sources=new ArrayList<>(); for(JsonNode x:n.path("sources")) sources.add(new PostSource(s(x,"title"),s(x,"publisher"),s(x,"url"),s(x,"publishedDate"),s(x,"relationship"),x.path("verified").asBoolean(false)));
                Post post=posts.save(new Post(s(n,"id"),persona,postTopics,s(n,"title"),s(n,"summary"),s(n,"caption"),GroundingMode.valueOf(s(n,"groundingMode")),sections,points,sources));
                JsonNode q=n.path("quiz"); List<QuizQuestion> questions=new ArrayList<>();
                for(JsonNode x:q.path("questions")){List<String> options=new ArrayList<>();x.path("options").forEach(v->options.add(v.asText()));questions.add(new QuizQuestion(s(x,"prompt"),x.path("keyPointOrder").asInt(),x.path("correctOption").asInt(),s(x,"explanation"),options));}
                if(!questions.isEmpty()) quizzes.save(new Quiz(s(q,"id"),post,questions));
            }
        }
    }
    private static String s(JsonNode n,String key){return n.path(key).asText("");}
}
