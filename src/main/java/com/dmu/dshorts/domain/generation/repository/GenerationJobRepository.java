package com.dmu.dshorts.domain.generation.repository;

import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByRequestKey(String requestKey);
}
