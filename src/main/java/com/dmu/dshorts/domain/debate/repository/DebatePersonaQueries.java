package com.dmu.dshorts.domain.debate.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Read-only persona lookups for debate DTOs (persona summaries). Visibility is checked separately. */
public interface DebatePersonaQueries extends Repository<Persona, Long> {

    List<Persona> findByIdIn(Collection<Long> ids);
}
