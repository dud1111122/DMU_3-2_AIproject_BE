package com.dmu.dshorts.domain.user.service;

import com.dmu.dshorts.domain.activity.entity.UserInterest;
import com.dmu.dshorts.domain.activity.repository.UserInterestRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.domain.user.dto.MyProfileResponse;
import com.dmu.dshorts.domain.user.dto.UpdateMeRequest;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.repository.UserRepository;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserInterestRepository userInterestRepository;
    private final TopicRepository topicRepository;

    public UserService(UserRepository userRepository, UserInterestRepository userInterestRepository,
                       TopicRepository topicRepository) {
        this.userRepository = userRepository;
        this.userInterestRepository = userInterestRepository;
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getProfile(Long userId) {
        return MyProfileResponse.of(loadUser(userId), interestTopicIds(userId));
    }

    @Transactional
    public MyProfileResponse updateProfile(Long userId, UpdateMeRequest request) {
        User user = loadUser(userId);
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw ApiException.conflict("이미 사용 중인 아이디입니다.");
            }
            user.setUsername(request.username());
        }
        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw ApiException.validation("displayName", "표시 이름은 1~50자여야 합니다.");
            }
            user.setDisplayName(displayName);
        }
        if (request.bio() != null) {
            user.setBio(request.bio().trim());
        }
        userRepository.flush();
        return MyProfileResponse.of(user, interestTopicIds(userId));
    }

    /** The session may outlive the row (e.g. DB reset); treat that as logged out. */
    private User loadUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(ApiException::unauthorized);
    }

    private List<Long> interestTopicIds(Long userId) {
        List<Long> topicIds = userInterestRepository.findByUserId(userId).stream()
                .map(UserInterest::getTopicId)
                .toList();
        if (topicIds.isEmpty()) {
            return List.of();
        }
        return VisibilityService.publicTopics(topicRepository.findAllById(topicIds)).stream()
                .map(Topic::getId)
                .toList();
    }
}
