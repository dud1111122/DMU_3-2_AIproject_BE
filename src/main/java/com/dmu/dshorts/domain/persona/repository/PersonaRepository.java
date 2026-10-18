package com.dmu.dshorts.domain.persona.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PersonaRepository extends JpaRepository<Persona,String> {}
