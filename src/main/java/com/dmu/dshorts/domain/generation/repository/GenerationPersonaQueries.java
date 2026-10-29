package com.dmu.dshorts.domain.generation.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Persona reads needed by preparation (read-only, generation-owned). */
public interface GenerationPersonaQueries extends Repository<Persona, Long> {

    /** Active, non-deleted personas in id order: the personas that get preparation jobs. */
    @Query("select pe from Persona pe where " + PersonaRepository.VISIBLE_PERSONA + " order by pe.id")
    List<Persona> findActivePersonas();
}
