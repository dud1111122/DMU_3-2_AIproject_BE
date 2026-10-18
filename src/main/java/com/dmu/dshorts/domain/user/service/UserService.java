package com.dmu.dshorts.domain.user.service;

import com.dmu.dshorts.domain.activity.entity.UserAction;
import com.dmu.dshorts.domain.activity.repository.UserActionRepository;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.dto.PostSummaryResponse;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.domain.user.entity.UserPreference;
import com.dmu.dshorts.domain.user.repository.UserPreferenceRepository;
import com.dmu.dshorts.global.common.PageResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final UserPreferenceRepository preferences;private final UserActionRepository actions;private final TopicRepository topics;private final PersonaRepository personas;private final CatalogService catalog;
    public UserService(UserPreferenceRepository preferences,UserActionRepository actions,TopicRepository topics,PersonaRepository personas,CatalogService catalog){this.preferences=preferences;this.actions=actions;this.topics=topics;this.personas=personas;this.catalog=catalog;}
    @Transactional public Map<String,Object> preferences(String user){
        UserPreference p=preferences.findById(user).orElseGet(()->preferences.save(new UserPreference(user)));
        var interestIds=actions.findByUserIdAndActionTypeOrderByCreatedAtDesc(user,"INTEREST").stream().map(UserAction::getTargetId).toList();
        return Map.of("mode",p.getMode(),"interestTopicIds",interestIds,"updatedAt",p.getUpdatedAt().toString());
    }
    @Transactional public Map<String,String> updateMode(String user,String mode){
        if(mode==null||!mode.matches("BASIC|INSTAGRAM|YOUTUBE"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"mode는 BASIC, INSTAGRAM, YOUTUBE 중 하나여야 합니다.");
        UserPreference p=preferences.findById(user).orElseGet(()->new UserPreference(user));p.setMode(mode);preferences.save(p);return Map.of("mode",mode);
    }
    @Transactional public Map<String,String> interest(String user,String topicId,boolean add){
        if(!topics.existsById(topicId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"주제를 찾을 수 없습니다.");
        action(user,"INTEREST",topicId,add);return Map.of("topicId",topicId,"selected",Boolean.toString(add));
    }
    @Transactional(readOnly=true) public PageResponse<PostSummaryResponse> saved(String user,String cursor,int limit){return catalog.savedPosts(user,cursor,limit);}
    @Transactional public Map<String,Object> activity(String user,String kind,String target,boolean add){
        if(kind.equals("FOLLOW")){if(!personas.existsById(target))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"페르소나를 찾을 수 없습니다.");}
        else catalog.requirePost(target);
        action(user,kind,target,add);
        return Map.of("targetId",target,"active",add,"count",kind.equals("LIKE")?actions.countByActionTypeAndTargetId(kind,target):actions.countByActionTypeAndTargetId(kind,target));
    }
    private void action(String user,String kind,String target,boolean add){
        boolean exists=actions.existsByUserIdAndActionTypeAndTargetId(user,kind,target);
        if(add&&!exists)actions.save(new UserAction(user,kind,target));
        if(!add&&exists)actions.deleteByUserIdAndActionTypeAndTargetId(user,kind,target);
    }
}
