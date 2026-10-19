package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    Optional<ReviewItem> findByUserIdAndKeyPointId(Long userId, Long keyPointId);
}
