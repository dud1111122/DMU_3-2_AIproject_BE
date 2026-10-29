package com.dmu.dshorts.domain.generation.repository;

import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import com.dmu.dshorts.domain.generation.entity.GenerationJobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByRequestKey(String requestKey);

    boolean existsByRequestKey(String requestKey);

    /** Jobs of one preparation run ({@code requestKey} prefix {@code "<runKey>:"}) in the given states. */
    List<GenerationJob> findByRequestKeyStartingWithAndStatusInOrderByIdAsc(String prefix,
                                                                          Collection<GenerationJobStatus> statuses);

    List<GenerationJob> findByRequestKeyStartingWithOrderByIdAsc(String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from GenerationJob j where j.id = :id")
    Optional<GenerationJob> findForUpdate(@Param("id") Long id);

    /** Startup recovery: jobs left RUNNING by a stopped process become FAILED (re-run them manually). */
    @Modifying
    @Query("update GenerationJob j set j.status = :failed, j.errorMessage = :message, j.finishedAt = :now"
            + " where j.status = :running")
    int failRunningJobs(@Param("running") GenerationJobStatus running,
                        @Param("failed") GenerationJobStatus failed,
                        @Param("message") String message,
                        @Param("now") Instant now);

    // Admin list: newest first, keyset on id.

    List<GenerationJob> findAllByOrderByIdDesc(Pageable page);

    List<GenerationJob> findByIdLessThanOrderByIdDesc(Long id, Pageable page);

    List<GenerationJob> findByStatusOrderByIdDesc(GenerationJobStatus status, Pageable page);

    List<GenerationJob> findByStatusAndIdLessThanOrderByIdDesc(GenerationJobStatus status, Long id, Pageable page);
}
