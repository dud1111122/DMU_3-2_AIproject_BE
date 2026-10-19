package com.dmu.dshorts.domain.post.repository;

import com.dmu.dshorts.domain.post.entity.PostSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostSourceRepository extends JpaRepository<PostSource, Long> {

    List<PostSource> findByPostIdOrderBySourceOrderAsc(Long postId);
}
