package com.dmu.dshorts.domain.persona.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    /**
     * JPQL condition for a publicly visible persona (active and not deleted). Requires the alias {@code pe}.
     * Usable inside {@code @Query} strings like {@link com.dmu.dshorts.domain.post.repository.PostRepository#VISIBLE_POST}.
     */
    String VISIBLE_PERSONA = "pe.active = true and pe.deletedAt is null";

    Optional<Persona> findByUsername(String username);

    /** Prefer {@code VisibilityService.getVisiblePersona(id)}. */
    @Query("select pe from Persona pe where pe.id = :id and " + VISIBLE_PERSONA)
    Optional<Persona> findVisibleById(@Param("id") Long id);

    @Query("select pe.id from Persona pe where pe.id in :ids and " + VISIBLE_PERSONA)
    List<Long> findVisibleIds(@Param("ids") Collection<Long> ids);
}
