package com.example.vnm.ruleengine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void inOperator_valueWithinAllowedList_passes() {
        Rule rule = new Rule("CHANNEL_IN", "channel", Operator.IN, List.of("BigC", "CoopMart", "Lazada"), Severity.ERROR);
        Map<String, Object> data = Map.of("channel", "Lazada");

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isTrue();
        assertThat(result.results().get(0).passed()).isTrue();
    }

    @Test
    void inOperator_valueOutsideAllowedList_fails() {
        Rule rule = new Rule("CHANNEL_IN", "channel", Operator.IN, List.of("BigC", "CoopMart"), Severity.ERROR);
        Map<String, Object> data = Map.of("channel", "Shopee");

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isFalse();
        assertThat(result.results().get(0).passed()).isFalse();
    }

    @Test
    void missingField_notNullRule_fails() {
        Rule rule = new Rule("NOTE_REQUIRED", "note", Operator.NOT_NULL, null, Severity.ERROR);
        Map<String, Object> data = Map.of("quantity", 100); // "note" key absent entirely

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isFalse();
        RuleResult r = result.results().get(0);
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).contains("note");
    }

    @Test
    void eqOperator_toleratesNumericTypeMismatch() {
        // rule authored with an int, but the actual field might arrive as a Long/String
        Rule rule = new Rule("QUANTITY_EQ", "quantity", Operator.EQ, 100, Severity.ERROR);

        assertThat(engine.evaluate(Map.of("quantity", 100L), List.of(rule)).passed()).isTrue();
        assertThat(engine.evaluate(Map.of("quantity", "100"), List.of(rule)).passed()).isTrue();
        assertThat(engine.evaluate(Map.of("quantity", 101), List.of(rule)).passed()).isFalse();
    }

    @Test
    void gtOperator_nonNumericField_failsWithoutThrowing() {
        Rule rule = new Rule("QUANTITY_GT", "quantity", Operator.GT, 0, Severity.ERROR);
        Map<String, Object> data = Map.of("quantity", "not-a-number");

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isFalse();
        assertThat(result.results().get(0).message()).isNotBlank();
    }

    @Test
    void ltOperator_valueBelowLimit_passes() {
        Rule rule = new Rule("QUANTITY_LT", "quantity", Operator.LT, 1000, Severity.ERROR);
        Map<String, Object> data = Map.of("quantity", 100);

        EvaluationResult result = engine.evaluate(data, List.of(rule));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void multipleErrorAndWarningRules_mixedOutcome() {
        Map<String, Object> data = new HashMap<>();
        data.put("quantity", 100);
        data.put("channel", "Shopee"); // violates the ERROR rule
        // "customerName" key is absent, violating the WARNING rule

        List<Rule> rules = List.of(
                new Rule("R1_QUANTITY_POSITIVE", "quantity", Operator.GT, 0, Severity.ERROR),
                new Rule("R2_CHANNEL_ALLOWED", "channel", Operator.IN, List.of("BigC", "CoopMart"), Severity.ERROR),
                new Rule("R3_CUSTOMER_NAME_PRESENT", "customerName", Operator.NOT_NULL, null, Severity.WARNING)
        );

        EvaluationResult result = engine.evaluate(data, rules);

        assertThat(result.passed()).isFalse();
        assertThat(findResult(result, "R1_QUANTITY_POSITIVE").passed()).isTrue();
        assertThat(findResult(result, "R2_CHANNEL_ALLOWED").passed()).isFalse();
        assertThat(findResult(result, "R3_CUSTOMER_NAME_PRESENT").passed()).isFalse();
    }

    private static RuleResult findResult(EvaluationResult result, String ruleId) {
        return result.results().stream()
                .filter(r -> r.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow();
    }

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
