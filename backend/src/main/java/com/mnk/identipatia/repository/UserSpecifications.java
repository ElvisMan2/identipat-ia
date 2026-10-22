package com.mnk.identipatia.repository;

import com.mnk.identipatia.model.User;
import com.mnk.identipatia.service.UserSearchCriteria;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecifications {

    private static final List<String> SEARCH_FIELDS = List.of(
            "firstName",
            "paternalLastName",
            "maternalLastName",
            "doi",
            "email",
            "profession");

    private UserSpecifications() {
    }

    public static Specification<User> matching(UserSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (String term : criteria.searchTerms()) {
                String pattern = "%" + escapeLike(term) + "%";
                Predicate[] fieldMatches = SEARCH_FIELDS.stream()
                        .map(field -> {
                            Expression<String> normalizedField = builder.lower(root.get(field));
                            return builder.like(normalizedField, pattern, '\\');
                        })
                        .toArray(Predicate[]::new);
                predicates.add(builder.or(fieldMatches));
            }

            addEqualFilter(predicates, builder, root.get("userType"), criteria.userType());
            addEqualFilter(predicates, builder, root.get("status"), criteria.status());
            addEqualFilter(predicates, builder, root.get("doiType"), criteria.doiType());

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addEqualFilter(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            Expression<String> field,
            String value) {
        if (value != null) {
            predicates.add(builder.equal(builder.upper(field), value));
        }
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
