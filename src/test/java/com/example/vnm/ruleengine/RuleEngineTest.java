package com.example.vnm.ruleengine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    /** BigC order validation rule set, as described in the scenario. */
    private static List<Rule> bigCRules() {
        return List.of(
                new Rule("R1_ORDER_ID_REQUIRED", "orderId", Operator.NOT_NULL, null, Severity.ERROR),
                new Rule("R2_CHANNEL_IS_BIGC", "channel", Operator.EQ, "BIGC", Severity.ERROR),
                new Rule("R3_AMOUNT_POSITIVE", "totalAmount", Operator.GT, 0, Severity.ERROR),
                new Rule("R4_AMOUNT_UNDER_LIMIT", "totalAmount", Operator.LT, 50_000_000, Severity.WARNING),
                new Rule("R5_STATUS_VALID", "status", Operator.IN, List.of("NEW", "CONFIRMED"), Severity.ERROR),
                new Rule("R6_NOTE_PRESENT", "note", Operator.NOT_NULL, null, Severity.WARNING)
        );
    }

    private static Map<String, Object> validOrder() {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", "ORD-001");
        data.put("channel", "BIGC");
        data.put("totalAmount", 150_000);
        data.put("status", "NEW");
        data.put("note", "leave at the door");
        return data;
    }

    @Test
    void validData_allRulesPass() {
        EvaluationResult result = engine.evaluate(validOrder(), bigCRules());

        assertThat(result.passed()).isTrue();
        assertThat(result.results()).hasSize(6);
        assertThat(result.results()).allMatch(RuleResult::passed);
    }

    @Test
    void errorRuleViolation_failsEvaluation() {
        Map<String, Object> data = validOrder();
        data.put("totalAmount", -100); // violates R3 (ERROR)

        EvaluationResult result = engine.evaluate(data, bigCRules());

        assertThat(result.passed()).isFalse();
        RuleResult r3 = findResult(result, "R3_AMOUNT_POSITIVE");
        assertThat(r3.passed()).isFalse();
        assertThat(r3.message()).isNotBlank();
    }

    @Test
    void warningRuleViolation_stillPassesButReportsWarning() {
        Map<String, Object> data = validOrder();
        data.remove("note"); // violates R6 (WARNING)

        EvaluationResult result = engine.evaluate(data, bigCRules());

        assertThat(result.passed()).isTrue();
        RuleResult r6 = findResult(result, "R6_NOTE_PRESENT");
        assertThat(r6.passed()).isFalse();
        assertThat(r6.message()).isNotBlank();
    }

    @Test
    void inOperator_valueWithinAllowedList_passes() {
        Rule rule = new Rule("STATUS_IN", "status", Operator.IN, List.of("NEW", "CONFIRMED", "SHIPPED"), Severity.ERROR);
        Map<String, Object> data = Map.of("status", "SHIPPED");

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isTrue();
        assertThat(result.results().get(0).passed()).isTrue();
    }

    @Test
    void inOperator_valueOutsideAllowedList_fails() {
        Rule rule = new Rule("STATUS_IN", "status", Operator.IN, List.of("NEW", "CONFIRMED"), Severity.ERROR);
        Map<String, Object> data = Map.of("status", "CANCELLED");

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isFalse();
        assertThat(result.results().get(0).passed()).isFalse();
    }

    @Test
    void missingField_notNullRule_fails() {
        Rule rule = new Rule("NOTE_REQUIRED", "note", Operator.NOT_NULL, null, Severity.ERROR);
        Map<String, Object> data = Map.of("orderId", "ORD-001"); // "note" key absent entirely

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isFalse();
        RuleResult r = result.results().get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).contains("note");
    }

    @Test
    void eqOperator_toleratesNumericTypeMismatch() {
        // rule authored with an int, but the actual order field arrives as a Long/String
        Rule rule = new Rule("AMOUNT_EQ", "totalAmount", Operator.EQ, 100, Severity.ERROR);

        assertThat(engine.evaluate(Map.of("totalAmount", 100L), List.of(rule)).passed()).isTrue();
        assertThat(engine.evaluate(Map.of("totalAmount", "100"), List.of(rule)).passed()).isTrue();
        assertThat(engine.evaluate(Map.of("totalAmount", 101), List.of(rule)).passed()).isFalse();
    }

    @Test
    void gtOperator_nonNumericField_failsWithoutThrowing() {
        Rule rule = new Rule("AMOUNT_GT", "totalAmount", Operator.GT, 0, Severity.ERROR);
        Map<String, Object> data = Map.of("totalAmount", "not-a-number");

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isFalse();
        assertThat(result.results().get(0).message()).isNotBlank();
    }

    @Test
    void multipleErrorAndWarningRules_mixedOutcome() {
        Map<String, Object> data = validOrder();
        data.put("channel", "COOPMART"); // violates R2 (ERROR)
        data.remove("note");             // violates R6 (WARNING)

        EvaluationResult result = engine.evaluate(data, bigCRules());

        assertThat(result.passed()).isFalse();
        assertThat(findResult(result, "R2_CHANNEL_IS_BIGC").passed()).isFalse();
        assertThat(findResult(result, "R6_NOTE_PRESENT").passed()).isFalse();
        assertThat(findResult(result, "R1_ORDER_ID_REQUIRED").passed()).isTrue();
    }

    private static RuleResult findResult(EvaluationResult result, String ruleId) {
        return result.results().stream()
                .filter(r -> r.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow();
    }

    /** The three worked examples from the case study prompt, reproduced verbatim. */
    @Nested
    class SpecExamples {

        @Test
        void testCase1_allRulesPass() {
            Map<String, Object> data = Map.of(
                    "quantity", 100,
                    "channel", "BigC",
                    "customerName", "Nguyen Van A"
            );
            List<Rule> rules = List.of(
                    new Rule("R1", "quantity", Operator.GT, 0, Severity.ERROR),
                    new Rule("R2", "channel", Operator.IN, List.of("BigC", "CoopMart", "Lazada"), Severity.ERROR),
                    new Rule("R3", "customerName", Operator.NOT_NULL, null, Severity.WARNING)
            );

            EvaluationResult result = engine.evaluate(data, rules);

            assertThat(result.passed()).isTrue();
            assertThat(findResult(result, "R1").passed()).isTrue();
            assertThat(findResult(result, "R2").passed()).isTrue();
            assertThat(findResult(result, "R3").passed()).isTrue();
        }

        @Test
        void testCase2_errorViolation_blocksAndReportsMessage() {
            Map<String, Object> data = new HashMap<>();
            data.put("quantity", -5);
            data.put("channel", "BigC");
            List<Rule> rules = List.of(
                    new Rule("R1", "quantity", Operator.GT, 0, Severity.ERROR)
            );

            EvaluationResult result = engine.evaluate(data, rules);

            assertThat(result.passed()).isFalse();
            RuleResult r1 = findResult(result, "R1");
            assertThat(r1.passed()).isFalse();
            assertThat(r1.message()).isEqualTo("quantity must be greater than 0, got -5");
        }

        @Test
        void testCase3_warningViolation_passesWithWarningMessage() {
            Map<String, Object> data = new HashMap<>();
            data.put("quantity", 100);
            data.put("note", null);
            List<Rule> rules = List.of(
                    new Rule("R1", "quantity", Operator.GT, 0, Severity.ERROR),
                    new Rule("R2", "note", Operator.NOT_NULL, null, Severity.WARNING)
            );

            EvaluationResult result = engine.evaluate(data, rules);

            assertThat(result.passed()).isTrue();
            assertThat(findResult(result, "R1").passed()).isTrue();
            RuleResult r2 = findResult(result, "R2");
            assertThat(r2.passed()).isFalse();
            assertThat(r2.message()).isEqualTo("note must not be null");
        }
    }
}
