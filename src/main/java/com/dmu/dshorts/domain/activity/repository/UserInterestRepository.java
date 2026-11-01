package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.UserInterest;
import com.dmu.dshorts.domain.activity.entity.UserInterestId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {

    List<UserInterest> findByUserId(Long userId);

    /** Idempotent add: inserts the row unless it already exists. */
    @Modifying
    @Query(value = "insert into user_interests (user_id, topic_id) values (:userId, :topicId) on conflict do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Modifying
    @Query("delete from UserInterest i where i.userId = :userId and i.topicId = :topicId")
    int deleteOne(@Param("userId") Long userId, @Param("topicId") Long topicId);

    /** The user's interest topic IDs that are not deleted. */
    @Query("select i.topicId from UserInterest i join Topic t on t.id = i.topicId"
            + " where i.userId = :userId and t.deletedAt is null")
    List<Long> findActiveTopicIds(@Param("userId") Long userId);
}
