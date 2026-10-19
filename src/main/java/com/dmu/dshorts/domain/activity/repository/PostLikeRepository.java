package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.PostLike;
import com.dmu.dshorts.domain.activity.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
}
