package com.dmu.dshorts.domain.post.repository;

import com.dmu.dshorts.domain.post.entity.KeyPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KeyPointRepository extends JpaRepository<KeyPoint, Long> {

    List<KeyPoint> findByPostIdOrderByPointOrderAsc(Long postId);
}
