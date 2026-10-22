package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.QuizAttempt;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    String MY_VISIBLE_ATTEMPTS = "select a.id as id, a.postId as postId, p.title as postTitle,"
            + " pe.id as personaId, pe.displayName as personaDisplayName,"
            + " a.correctCount as correctCount, a.totalCount as totalCount, a.submittedAt as submittedAt"
            + " from QuizAttempt a join Post p on p.id = a.postId join p.persona pe"
            + " where a.userId = :userId and " + PostRepository.VISIBLE_POST;

    Optional<QuizAttempt> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query(MY_VISIBLE_ATTEMPTS + " order by a.submittedAt desc, a.id desc")
    List<AttemptRow> findMyVisibleFirstPage(@Param("userId") Long userId, Pageable page);

    @Query(MY_VISIBLE_ATTEMPTS
            + " and (a.submittedAt < :time or (a.submittedAt = :time and a.id < :id))"
            + " order by a.submittedAt desc, a.id desc")
    List<AttemptRow> findMyVisibleAfter(@Param("userId") Long userId, @Param("time") Instant time,
                                        @Param("id") Long id, Pageable page);

    /** Projection for the attempt history list. */
    interface AttemptRow {
        Long getId();

        Long getPostId();

        String getPostTitle();

        Long getPersonaId();

        String getPersonaDisplayName();

        Short getCorrectCount();

        Short getTotalCount();

        Instant getSubmittedAt();
    }
}
