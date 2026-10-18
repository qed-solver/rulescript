package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRuleInstances.EliminateExistsZeroRows;
import org.qed.RuleBuilder;

public final class EliminateExistsZeroRowsTest {
    public static void runTest() {
        var rule = new EliminateExistsZeroRows();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        handBuiltNonMatch();
    }

    private static void handBuiltMatch(EliminateExistsZeroRows rule) {
        var schema = RuleBuilder.create();
        var outerName = RuleChecks.table(schema, 1, false);
        var inputName = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        RelNode outer = builder.scan(outerName).build();
        RelNode input = builder.scan(inputName).build();
        RelNode empty = LogicalValues.create(input.getCluster(), input.getRowType(),
                com.google.common.collect.ImmutableList.of());
        var condition = RexSubQuery.exists(empty);
        RelNode before = LogicalFilter.create(outer, condition);
        RelNode after = LogicalFilter.create(outer,
                builder.getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(rule, before, after);
    }

    private static void handBuiltNonMatch() {
        var schema = RuleBuilder.create();
        var outerName = RuleChecks.table(schema, 1, false);
        var inputName = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        RelNode outer = builder.scan(outerName).build();
        RelNode input = builder.scan(inputName).build();
        RuleChecks.doesNotApply(new EliminateExistsZeroRows(), LogicalFilter.create(outer,
                RexSubQuery.exists(input)));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateExistsZeroRows test...");
        runTest();
    }
}
