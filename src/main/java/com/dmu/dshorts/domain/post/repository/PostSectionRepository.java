package com.dmu.dshorts.domain.post.repository;

import com.dmu.dshorts.domain.post.entity.PostSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostSectionRepository extends JpaRepository<PostSection, Long> {

    List<PostSection> findByPostIdOrderBySectionOrderAsc(Long postId);

    /** Cards of many posts in one query, grouped by post and ordered by section order. */
    List<PostSection> findByPostIdInOrderByPostIdAscSectionOrderAsc(Collection<Long> postIds);
}
