package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.Follow;
import com.dmu.dshorts.domain.activity.entity.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {
}
