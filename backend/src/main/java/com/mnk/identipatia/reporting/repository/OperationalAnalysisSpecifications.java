package com.mnk.identipatia.reporting.repository;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisFilters;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class OperationalAnalysisSpecifications {
    private static final List<String> USER_SEARCH_FIELDS = List.of(
            "firstName", "paternalLastName", "maternalLastName", "doi", "email");

    private OperationalAnalysisSpecifications() {
    }

    public static Specification<Analysis> matching(OperationalAnalysisFilters criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!criteria.searchTerms().isEmpty()) {
                Subquery<Long> matchingUser = query.subquery(Long.class);
                Root<User> user = matchingUser.from(User.class);
                List<Predicate> userPredicates = new ArrayList<>();
                userPredicates.add(builder.equal(user.get("userId"), root.get("userId")));
                for (String term : criteria.searchTerms()) {
                    String pattern = "%" + escapeLike(term) + "%";
                    Predicate[] fieldMatches = USER_SEARCH_FIELDS.stream()
                            .map(field -> {
                                Expression<String> normalized = builder.lower(user.get(field));
                                return builder.like(normalized, pattern, '\\');
                            })
                            .toArray(Predicate[]::new);
                    userPredicates.add(builder.or(fieldMatches));
                }
                matchingUser.select(user.get("userId"))
                        .where(builder.and(userPredicates.toArray(Predicate[]::new)));
                predicates.add(builder.exists(matchingUser));
            }

            if (criteria.fromInclusive() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("createdAt"), criteria.fromInclusive()));
            }
            if (criteria.toExclusive() != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), criteria.toExclusive()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.inputType() != null) {
                predicates.add(builder.equal(root.get("inputType"), criteria.inputType()));
            }
            if (criteria.origin() == OperationalAnalysisOrigin.STANDARD) {
                predicates.add(builder.isNotNull(root.get("sessionId")));
            } else if (criteria.origin() == OperationalAnalysisOrigin.ADMIN) {
                predicates.add(builder.isNull(root.get("sessionId")));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
