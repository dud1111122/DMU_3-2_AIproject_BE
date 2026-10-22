package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.post.entity.KeyPoint;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Read-only key point lookups for the learning package (table owned by domain/post). */
public interface LearningKeyPointQueries extends Repository<KeyPoint, Long> {

    List<KeyPoint> findByPostIdOrderByPointOrderAsc(Long postId);
}
