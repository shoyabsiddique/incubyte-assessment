package com.acme.salary.audit.persistence;

import com.acme.salary.audit.domain.AuditQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Read access to the audit trail: filtered pages, newest first. There is deliberately no write here. */
@Repository
public class AuditEntryStore {

    private final EntityManager entityManager;

    AuditEntryStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<AuditEntryEntity> page(AuditQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditEntryEntity> criteria = cb.createQuery(AuditEntryEntity.class);
        Root<AuditEntryEntity> root = criteria.from(AuditEntryEntity.class);
        criteria.select(root)
                .where(predicates(query, root, cb))
                .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        return entityManager
                .createQuery(criteria)
                .setFirstResult(query.page() * query.size())
                .setMaxResults(query.size())
                .getResultList();
    }

    public long count(AuditQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = cb.createQuery(Long.class);
        Root<AuditEntryEntity> root = criteria.from(AuditEntryEntity.class);
        criteria.select(cb.count(root)).where(predicates(query, root, cb));
        return entityManager.createQuery(criteria).getSingleResult();
    }

    private static Predicate[] predicates(AuditQuery query, Root<AuditEntryEntity> root, CriteriaBuilder cb) {
        List<Predicate> all = new ArrayList<>();
        query.userId().ifPresent(id -> all.add(cb.equal(root.get("userId"), id)));
        query.entityType().ifPresent(type -> all.add(cb.equal(root.get("entityType"), type.name())));
        query.from().ifPresent(from -> all.add(cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"), from)));
        query.toExclusive().ifPresent(to -> all.add(cb.lessThan(root.<java.time.Instant>get("createdAt"), to)));
        query.subjectEmployeeId().ifPresent(id -> all.add(cb.equal(root.get("subjectEmployeeId"), id)));
        return all.toArray(new Predicate[0]);
    }
}
