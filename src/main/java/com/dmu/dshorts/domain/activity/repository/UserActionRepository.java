package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.UserAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActionRepository extends JpaRepository<UserAction,Long> {
    boolean existsByUserIdAndActionTypeAndTargetId(String userId,String actionType,String targetId);
    void deleteByUserIdAndActionTypeAndTargetId(String userId,String actionType,String targetId);
    List<UserAction> findByUserIdAndActionTypeOrderByCreatedAtDesc(String userId,String actionType);
    long countByActionTypeAndTargetId(String actionType,String targetId);
}
