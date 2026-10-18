package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateRedundantBarrier;
import org.qed.RuleBuilder;

public class EliminateRedundantBarrierTest {
    public static void runTest() {
        var rule = new EliminateRedundantBarrier();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var source = b.scan(table).build();
        var refs = java.util.List.<org.apache.calcite.rex.RexNode>of(b.getRexBuilder().makeInputRef(
                source.getRowType().getFieldList().get(0).getType(), 0));
        var inner = org.apache.calcite.rel.logical.LogicalProject.create(source, java.util.List.of(), refs,
                (java.util.List<String>) null);
        var positive = org.apache.calcite.rel.logical.LogicalProject.create(inner, java.util.List.of(), refs,
                (java.util.List<String>) null);
        var expectedExpr = b.getRexBuilder().makeCast(positive.getRowType().getFieldList().get(0).getType(),
                b.getRexBuilder().makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0), true);
        var expected = org.apache.calcite.rel.logical.LogicalProject.create(source, java.util.List.of(),
                java.util.List.of(expectedExpr), positive.getRowType().getFieldNames());
        RuleChecks.rewrites(rule, positive, expected);

        var noMatchBuilder = RuleBuilder.create();
        var noMatchTable = RuleChecks.table(noMatchBuilder, 1, false);
        var nb = RuleChecks.ruleBuilder(noMatchBuilder);
        var noMatchSource = nb.scan(noMatchTable).build();
        var noMatch = org.apache.calcite.rel.logical.LogicalProject.create(noMatchSource,
                java.util.List.of(),
                java.util.List.<org.apache.calcite.rex.RexNode>of(nb.getRexBuilder().makeInputRef(
                        noMatchSource.getRowType().getFieldList().get(0).getType(), 0)), (java.util.List<String>) null);
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateRedundantBarrier test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " test(s) failed");
        }
    }
}
