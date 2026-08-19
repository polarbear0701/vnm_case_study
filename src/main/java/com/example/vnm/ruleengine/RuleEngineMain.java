package com.example.vnm.ruleengine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleEngineMain {

    public static void main(String[] args) {
        testCase1_allRulesPass();
        testCase2_errorViolation();
        testCase3_warningViolation();
    }

    private static void testCase1_allRulesPass() {
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

        run("Test case 1 - all rules pass", data, rules);
    }

    private static void testCase2_errorViolation() {
        Map<String, Object> data = new HashMap<>();
        data.put("quantity", -5);
        data.put("channel", "BigC");
        List<Rule> rules = List.of(
                new Rule("R1", "quantity", Operator.GT, 0, Severity.ERROR)
        );

        run("Test case 2 - ERROR violation blocks", data, rules);
    }

    private static void testCase3_warningViolation() {
        Map<String, Object> data = new HashMap<>();
        data.put("quantity", 100);
        data.put("note", null);
        List<Rule> rules = List.of(
                new Rule("R1", "quantity", Operator.GT, 0, Severity.ERROR),
                new Rule("R2", "note", Operator.NOT_NULL, null, Severity.WARNING)
        );

        run("Test case 3 - WARNING violation still passes", data, rules);
    }

    private static void run(String label, Map<String, Object> data, List<Rule> rules) {
        EvaluationResult result = new RuleEngine().evaluate(data, rules);

        System.out.println("=== " + label + " ===");
        System.out.println("data: " + data);
        System.out.println("passed: " + result.passed());
        for (RuleResult r : result.results()) {
            String status = r.passed() ? "PASS" : "FAIL";
            System.out.println("  [" + status + "] " + r.ruleId() + (r.message() != null ? " - " + r.message() : ""));
        }
        System.out.println();
    }
}
