package com.dmu.dshorts.domain.admin.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Admin reads of personas: no visibility filter (inactive and deleted included). */
public interface AdminPersonaQueries extends Repository<Persona, Long> {

    @Query("select distinct pe from Persona pe left join fetch pe.topics order by pe.id")
    List<Persona> findAllWithTopics();

    @Query("select pe from Persona pe left join fetch pe.topics where pe.id = :id")
    Optional<Persona> findWithTopicsById(@Param("id") Long id);
}
