package com.example.vnm.ruleengine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RuleEngine {

    public EvaluationResult evaluate(Map<String, Object> data, List<Rule> rules) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(rules, "rules must not be null");

        List<RuleResult> results = new ArrayList<>(rules.size());
        boolean passed = true;
        for (Rule rule : rules) {
            RuleResult result = evaluateRule(data, rule);
            results.add(result);
            if (rule.severity() == Severity.ERROR && !result.passed()) {
                passed = false;
            }
        }
        return new EvaluationResult(passed, List.copyOf(results));
    }

    private RuleResult evaluateRule(Map<String, Object> data, Rule rule) {
        boolean present = data.containsKey(rule.field());
        Object actual = data.get(rule.field());

        try {
            boolean passed = switch (rule.operator()) {
                case NOT_NULL -> present && actual != null;
                case EQ -> present && actual != null && valuesEqual(actual, rule.value());
                case GT -> present && actual != null && compareNumeric(actual, rule.value()) > 0;
                case LT -> present && actual != null && compareNumeric(actual, rule.value()) < 0;
                case IN -> present && actual != null && isIn(actual, rule.value());
            };
            return new RuleResult(rule.ruleId(), passed, passed ? null : failureMessage(rule, actual));
        } catch (RuntimeException e) {
            // A malformed value (e.g. non-numeric field for GT/LT) must not crash the
            // whole evaluation - surface it as a failed rule instead.
            return new RuleResult(rule.ruleId(), false,
                    "Rule '%s' could not be evaluated: %s".formatted(rule.ruleId(), e.getMessage()));
        }
    }

    private String failureMessage(Rule rule, Object actual) {
        return switch (rule.operator()) {
            case NOT_NULL -> "%s must not be null".formatted(rule.field());
            case EQ -> "%s must equal %s, got %s".formatted(rule.field(), rule.value(), actual);
            case GT -> "%s must be greater than %s, got %s".formatted(rule.field(), rule.value(), actual);
            case LT -> "%s must be less than %s, got %s".formatted(rule.field(), rule.value(), actual);
            case IN -> "%s must be one of %s, got %s".formatted(rule.field(), rule.value(), actual);
        };
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (isNumeric(actual) && isNumeric(expected)) {
            return compareNumeric(actual, expected) == 0;
        }
        return Objects.equals(String.valueOf(actual), String.valueOf(expected));
    }

    private boolean isIn(Object actual, Object allowedValues) {
        if (!(allowedValues instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("IN operator requires a Collection value");
        }
        return collection.stream().anyMatch(candidate -> valuesEqual(actual, candidate));
    }

    private int compareNumeric(Object a, Object b) {
        return toBigDecimal(a).compareTo(toBigDecimal(b));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    private boolean isNumeric(Object value) {
        try {
            toBigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
