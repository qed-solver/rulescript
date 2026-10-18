package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexInputRef;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PartitionPruning;
import org.qed.RuleBuilder;

public class PartitionPruningTest {
    public static void runTest() {
        var rule = new PartitionPruning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PartitionPruning rule) {
        var beforeRules = RuleBuilder.create();
        var build = RuleChecks.table(beforeRules, 1, false, false);
        var probe = RuleChecks.table(beforeRules, 1, false, false);
        var beforeBuilder = RuleChecks.ruleBuilder(beforeRules);
        var before = beforeBuilder.scan(build).scan(probe)
                .join(JoinRelType.INNER, RuleChecks.eq(beforeBuilder,
                        new RexInputRef(0, beforeBuilder.peek(0).getRowType().getFieldList().get(0).getType()),
                        new RexInputRef(1, beforeBuilder.peek(1).getRowType().getFieldList().get(0).getType())))
                .build();

        var afterRules = RuleBuilder.create();
        var afterBuild = RuleChecks.table(afterRules, 1, false, false);
        var afterProbe = RuleChecks.table(afterRules, 1, false, false);
        var afterBuilder = RuleChecks.ruleBuilder(afterRules);
        var filteredBuild = afterBuilder.scan(afterBuild).scan(afterProbe)
                .join(JoinRelType.SEMI, RuleChecks.eq(afterBuilder,
                        new RexInputRef(0, afterBuilder.peek(0).getRowType().getFieldList().get(0).getType()),
                        new RexInputRef(1, afterBuilder.peek(1).getRowType().getFieldList().get(0).getType())));
        var after = filteredBuild.scan(afterProbe)
                .join(JoinRelType.INNER, RuleChecks.eq(afterBuilder,
                        new RexInputRef(0, afterBuilder.peek(0).getRowType().getFieldList().get(0).getType()),
                        new RexInputRef(1, afterBuilder.peek(1).getRowType().getFieldList().get(0).getType())))
                .build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running PartitionPruning test...");
        runTest();
    }
}
