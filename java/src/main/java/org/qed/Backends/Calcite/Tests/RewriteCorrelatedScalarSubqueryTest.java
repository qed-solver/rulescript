package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RewriteCorrelatedScalarSubquery;

public final class RewriteCorrelatedScalarSubqueryTest {
    public static void runTest() {
        var rule = new RewriteCorrelatedScalarSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltCase();
        rejectsWrongAggregateInput();
    }

    private static void rewritesHandBuiltCase() {
        var config = org.qed.RuleBuilder.create();
        var outerName = RuleChecks.table(config, 1, true);
        var innerName = RuleChecks.table(config, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(config);

        RelNode correlate = buildCorrelatedInput(config, b, outerName, innerName);
        b.push(correlate);
        b.aggregate(b.groupKey(b.field(0)), b.count(false, "agg", b.field(1)));
        RelNode input = b.build();
        RelBuilder expectedBuilder = RuleChecks.ruleBuilder(config);
        expectedBuilder.scan(outerName).scan(innerName).join(JoinRelType.LEFT,
                expectedBuilder.call(SqlStdOperatorTable.EQUALS,
                        expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0)));
        var group = expectedBuilder.groupKey(expectedBuilder.field(0));
        var count = expectedBuilder.count(false, "agg", expectedBuilder.field(1));
        expectedBuilder.aggregate(group, count);
        RelNode expected = expectedBuilder.build();

        check(correlate.getRowType().getFieldCount() == 2, "hand-built correlate has an unexpected schema");
        RuleChecks.rewrites(new RewriteCorrelatedScalarSubquery(), input, expected);
    }

    private static RelNode buildCorrelatedInput(org.qed.RuleBuilder config, RelBuilder b,
                                                 String outerName, String innerName) {
        b.scan(outerName);
        b.scan(innerName);
        RelNode inner = b.build();
        RelNode outer = b.build();
        return RuleChecks.correlate(b, outer, inner, JoinRelType.LEFT, args ->
                b.call(SqlStdOperatorTable.EQUALS, args[0], args[1]));
    }

    private static void rejectsWrongAggregateInput() {
        var builder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, true);
        var rightName = RuleChecks.table(builder, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        b.scan(leftName);
        RelNode corrLeft = b.build();
        b.scan(rightName);
        RelNode corrRight = b.build();
        RelNode corr = RuleChecks.correlate(b, corrLeft, corrRight, JoinRelType.LEFT,
                args -> b.call(SqlStdOperatorTable.EQUALS, args[0], args[1]));
        b.push(corr);
        var group = b.groupKey(b.field(0));
        b.aggregate(group, b.count(false, "agg", b.field(0)));
        RuleChecks.doesNotApply(new RewriteCorrelatedScalarSubquery(), b.build());
    }

    private static void check(boolean condition, String message) {
        if (!condition) CalciteTester.fail(message);
    }

    public static void main(String[] args) {
        System.out.println("Running RewriteCorrelatedScalarSubquery test...");
        runTest();
    }
}
