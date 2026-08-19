package com.example.vnm.ruleengine;

import java.util.Collection;
import java.util.Objects;

public record Rule(String ruleId, String field, Operator operator, Object value, Severity severity) {

    public Rule {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (operator != Operator.NOT_NULL && value == null) {
            throw new IllegalArgumentException("value is required for operator " + operator);
        }
        if (operator == Operator.IN && !(value instanceof Collection<?>)) {
            throw new IllegalArgumentException("value for IN operator must be a Collection");
        }
    }
}
