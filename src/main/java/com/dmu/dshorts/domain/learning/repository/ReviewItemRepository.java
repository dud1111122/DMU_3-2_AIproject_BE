package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewItemRepository extends JpaRepository<ReviewItem,Long> {
    List<ReviewItem> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<ReviewItem> findByUserIdAndPostIdAndKeyPointOrder(String userId,String postId,Integer keyPointOrder);
}
