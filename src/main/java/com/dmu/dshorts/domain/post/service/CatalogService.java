package com.dmu.dshorts.domain.post.service;

import com.dmu.dshorts.domain.activity.repository.UserActionRepository;
import com.dmu.dshorts.domain.persona.dto.PersonaResponse;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.dto.*;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.topic.dto.TopicResponse;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.common.PageResponse;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogService {
    private final TopicRepository topics; private final PersonaRepository personas; private final PostRepository posts; private final UserActionRepository actions;
    public CatalogService(TopicRepository topics,PersonaRepository personas,PostRepository posts,UserActionRepository actions){this.topics=topics;this.personas=personas;this.posts=posts;this.actions=actions;}

    @Transactional(readOnly=true)
    public List<TopicResponse> topics(){return topics.findAll().stream().sorted(Comparator.comparing(t->t.getName())).map(t->new TopicResponse(t.getId(),t.getName(),t.getDescription())).toList();}

    @Transactional(readOnly=true)
    public PageResponse<PersonaResponse> personas(String q,String topicId,String cursor,int limit,String user){
        Set<String> matching=topicId==null?null:posts.findByPublishedTrueAndHiddenFalseOrderByCreatedAtDescIdAsc().stream().filter(p->p.getTopics().stream().anyMatch(t->t.getId().equals(topicId))).map(p->p.getPersona().getId()).collect(Collectors.toSet());
        String query=q==null?"":q.toLowerCase(Locale.ROOT);
        var found=personas.findAll().stream().filter(p->query.isBlank()||contains(p.getName(),query)||contains(p.getField(),query)||contains(p.getBio(),query))
            .filter(p->matching==null||matching.contains(p.getId())).map(p->personaDto(p,user)).toList();
        return pageResponse(found,cursor,limit);
    }

    @Transactional(readOnly=true)
    public PersonaResponse persona(String id,String user){return personaDto(personas.findById(id).orElseThrow(()->notFound("페르소나를 찾을 수 없습니다.")),user);}

    @Transactional(readOnly=true)
    public List<PostSummaryResponse> personaPosts(String personaId,String user){
        if(!personas.existsById(personaId)) throw notFound("페르소나를 찾을 수 없습니다.");
        return posts.findByPersonaIdAndPublishedTrueAndHiddenFalseOrderByCreatedAtDesc(personaId).stream().map(p->summary(p,user)).toList();
    }

    @Transactional(readOnly=true)
    public PostDetailResponse post(String id,String user){return detail(requirePost(id),user);}

    @Transactional(readOnly=true)
    public PageResponse<PostSummaryResponse> feed(String topicId,boolean following,String cursor,int limit,String user){
        List<Post> all=posts.findByPublishedTrueAndHiddenFalseOrderByCreatedAtDescIdAsc();
        Set<String> followed=actions.findByUserIdAndActionTypeOrderByCreatedAtDesc(user,"FOLLOW").stream().map(a->a.getTargetId()).collect(Collectors.toSet());
        List<PostSummaryResponse> matching=all.stream().filter(p->topicId==null||p.getTopics().stream().anyMatch(t->t.getId().equals(topicId)))
            .filter(p->!following||followed.contains(p.getPersona().getId())).map(p->summary(p,user)).toList();
        return pageResponse(matching,cursor,limit);
    }

    @Transactional(readOnly=true)
    public PageResponse<PostSummaryResponse> personaPostPage(String personaId,String cursor,int limit,String user){return pageResponse(personaPosts(personaId,user),cursor,limit);}

    @Transactional(readOnly=true)
    public PageResponse<Map<String,Object>> search(String q,String type,String cursor,int limit,String user){
        if(q==null||q.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"검색어 q가 필요합니다.");
        String query=q.toLowerCase(Locale.ROOT); String selected=type==null?"all":type.toLowerCase(Locale.ROOT);
        if(!Set.of("all","persona","topic","post").contains(selected))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"type은 all, persona, topic, post 중 하나여야 합니다.");
        List<Map<String,Object>> items=new ArrayList<>();
        if(selected.equals("all")||selected.equals("persona")) for(Persona p:personas.findAll()) if(contains(p.getName(),query)||contains(p.getField(),query)||contains(p.getBio(),query)) items.add(Map.of("type","persona","id",p.getId(),"title",p.getName(),"subtitle",p.getField()));
        if(selected.equals("all")||selected.equals("topic")) for(var t:topics.findAll()) if(contains(t.getName(),query)||contains(t.getDescription(),query)) items.add(Map.of("type","topic","id",t.getId(),"title",t.getName(),"subtitle",t.getDescription()));
        if(selected.equals("all")||selected.equals("post")) for(Post p:posts.findByPublishedTrueAndHiddenFalseOrderByCreatedAtDescIdAsc()) if(contains(p.getTitle(),query)||contains(p.getCaption(),query)||contains(p.getSummary(),query)) items.add(Map.of("type","post","id",p.getId(),"title",p.getTitle(),"subtitle",p.getPersona().getName()));
        items.sort(Comparator.comparing(m->m.get("title").toString()));
        return pageResponse(items,cursor,limit);
    }

    @Transactional(readOnly=true)
    public PageResponse<PostSummaryResponse> savedPosts(String user,String cursor,int limit){
        List<String> ids=actions.findByUserIdAndActionTypeOrderByCreatedAtDesc(user,"SAVE").stream().map(a->a.getTargetId()).toList();
        List<PostSummaryResponse> found=ids.stream().map(posts::findById).flatMap(Optional::stream).filter(p->p.isPublished()&&!p.isHidden()).map(p->summary(p,user)).toList();
        return pageResponse(found,cursor,limit);
    }

    public Post requirePost(String id){return posts.findById(id).filter(p->p.isPublished()&&!p.isHidden()).orElseThrow(()->notFound("게시물을 찾을 수 없습니다."));}
    public PostSummaryResponse summary(Post p,String user){
        return new PostSummaryResponse(p.getId(),p.getTitle(),p.getSummary(),p.getCaption(),p.getGroundingMode().name(),p.getPersona().getId(),p.getPersona().getName(),p.getPersona().getHandle(),p.getTopics().stream().map(t->t.getId()).toList(),p.getTopics().stream().map(t->t.getName()).toList(),actions.countByActionTypeAndTargetId("LIKE",p.getId()),actions.existsByUserIdAndActionTypeAndTargetId(user,"LIKE",p.getId()),actions.existsByUserIdAndActionTypeAndTargetId(user,"SAVE",p.getId()),p.getCreatedAt());
    }
    public PostDetailResponse detail(Post p,String user){
        var sections=new ArrayList<PostSectionResponse>(); for(int i=0;i<p.getSections().size();i++){var s=p.getSections().get(i);sections.add(new PostSectionResponse(i,s.getTitle(),s.getBody(),s.getMediaType(),s.getMediaUrl(),s.getAltText(),s.getExpressionType()));}
        return new PostDetailResponse(summary(p,user),sections,p.getKeyPoints().stream().map(k->k.getText()).toList(),p.getSources().stream().map(s->new PostSourceResponse(s.getTitle(),s.getPublisher(),s.getUrl(),s.getPublishedDate(),s.getRelationship(),s.isVerified())).toList());
    }
    private PersonaResponse personaDto(Persona p,String user){return new PersonaResponse(p.getId(),p.getName(),p.getHandle(),p.getEra(),p.getField(),p.getAvatarUrl(),p.getBio(),"AI로 재현한 페르소나입니다. 실제 인물의 발언이 아닙니다.",actions.existsByUserIdAndActionTypeAndTargetId(user,"FOLLOW",p.getId()));}
    private static boolean contains(String value,String query){return value!=null&&value.toLowerCase(Locale.ROOT).contains(query);}
    public static <T> PageResponse<T> pageResponse(List<T> all,String cursor,int limit){int offset=parseCursor(cursor);int size=Math.min(Math.max(limit,1),50);int end=Math.min(offset+size,all.size());List<T> items=offset>=all.size()?List.of():all.subList(offset,end);String next=end<all.size()?Integer.toString(end):null;return new PageResponse<>(items,next);}
    private static int parseCursor(String cursor){try{return cursor==null?0:Math.max(0,Integer.parseInt(cursor));}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"cursor는 숫자여야 합니다.");}}
    private static ResponseStatusException notFound(String message){return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
}
