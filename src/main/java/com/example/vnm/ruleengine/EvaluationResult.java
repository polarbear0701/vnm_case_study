package com.example.vnm.ruleengine;

import java.util.List;

public record EvaluationResult(boolean passed, List<RuleResult> results) {
}
