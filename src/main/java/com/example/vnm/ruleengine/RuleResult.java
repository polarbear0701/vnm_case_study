package com.example.vnm.ruleengine;

public record RuleResult(String ruleId, boolean passed, String message) {
}
