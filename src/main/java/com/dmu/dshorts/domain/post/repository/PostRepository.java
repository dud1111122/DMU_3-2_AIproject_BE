package com.dmu.dshorts.domain.post.repository;

import com.dmu.dshorts.domain.post.entity.Post;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post,String> {
    List<Post> findByPublishedTrueAndHiddenFalseOrderByCreatedAtDescIdAsc();
    List<Post> findByPersonaIdAndPublishedTrueAndHiddenFalseOrderByCreatedAtDesc(String personaId);
}
