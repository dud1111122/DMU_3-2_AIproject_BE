package com.dmu.dshorts.domain.persona.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keyset list queries over visible personas (active, not deleted) with optional filters. */
@Repository
public class PersonaQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Visible personas ordered by id asc, with id above {@code afterId}.
     *
     * @param pattern        optional LIKE pattern on display name, username or bio
     * @param topicId        optional: only personas linked to this non-deleted topic
     * @param followerUserId optional: only personas followed by this user
     */
    public List<Persona> findVisible(String pattern, Long topicId, Long followerUserId, Long afterId, int maxResults) {
        StringBuilder jpql = new StringBuilder("select pe from Persona pe where ")
                .append(PersonaRepository.VISIBLE_PERSONA);
        Map<String, Object> params = new HashMap<>();
        if (pattern != null) {
            jpql.append(" and (lower(pe.displayName) like :pattern escape '!'"
                    + " or lower(pe.username) like :pattern escape '!'"
                    + " or lower(pe.bio) like :pattern escape '!')");
            params.put("pattern", pattern);
        }
        if (topicId != null) {
            jpql.append(" and exists (select 1 from Persona x join x.topics t"
                    + " where x.id = pe.id and t.id = :topicId and t.deletedAt is null)");
            params.put("topicId", topicId);
        }
        if (followerUserId != null) {
            jpql.append(" and exists (select 1 from Follow f where f.userId = :followerUserId and f.personaId = pe.id)");
            params.put("followerUserId", followerUserId);
        }
        if (afterId != null) {
            jpql.append(" and pe.id > :afterId");
            params.put("afterId", afterId);
        }
        jpql.append(" order by pe.id asc");
        return run(jpql.toString(), params, maxResults);
    }

    /** Persona search: display name or username contains the pattern, id desc, with id below {@code beforeId}. */
    public List<Persona> searchByName(String pattern, Long beforeId, int maxResults) {
        StringBuilder jpql = new StringBuilder("select pe from Persona pe where ")
                .append(PersonaRepository.VISIBLE_PERSONA)
                .append(" and (lower(pe.displayName) like :pattern escape '!'"
                        + " or lower(pe.username) like :pattern escape '!')");
        Map<String, Object> params = new HashMap<>();
        params.put("pattern", pattern);
        if (beforeId != null) {
            jpql.append(" and pe.id < :beforeId");
            params.put("beforeId", beforeId);
        }
        jpql.append(" order by pe.id desc");
        return run(jpql.toString(), params, maxResults);
    }

    private List<Persona> run(String jpql, Map<String, Object> params, int maxResults) {
        TypedQuery<Persona> query = entityManager.createQuery(jpql, Persona.class);
        params.forEach(query::setParameter);
        query.setMaxResults(maxResults);
        return query.getResultList();
    }
}
