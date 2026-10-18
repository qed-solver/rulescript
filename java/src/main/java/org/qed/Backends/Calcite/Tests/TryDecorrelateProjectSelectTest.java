package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryDecorrelateProjectSelect;
import org.qed.RuleBuilder;

public class TryDecorrelateProjectSelectTest {
    public static void runTest() {
        var rule = new TryDecorrelateProjectSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsUnfilteredRightInput(rule);
    }

    private static void rewritesHandBuiltPlan(TryDecorrelateProjectSelect rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(l).build();
        RelNode sourceRight = b.scan(r)
                .filter(b.call(SqlStdOperatorTable.GREATER_THAN, b.field(0), b.literal(0)))
                .project(b.field(1)).build();
        var rex = b.getRexBuilder();
        var source = b.push(left).push(sourceRight).join(JoinRelType.LEFT,
                RuleChecks.eq(b, rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                        rex.makeInputRef(sourceRight.getRowType().getFieldList().get(0).getType(), 1))).build();

        RelNode right = b.scan(r).project(b.field(0), b.field(1)).build();
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                RuleChecks.eq(b, rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                        rex.makeInputRef(right.getRowType().getFieldList().get(1).getType(), 2)),
                rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                        rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1), b.literal(0)));
        RelNode expected = b.push(left).push(right).join(JoinRelType.LEFT, condition)
                .project(b.field(0), b.field(2)).build();
        RuleChecks.rewrites(rule, source, expected);
    }

    private static void rejectsUnfilteredRightInput(TryDecorrelateProjectSelect rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(l).build();
        RelNode right = b.scan(r).project(b.field(1)).build();
        RelNode plan = RuleChecks.correlate(b, left, right, JoinRelType.LEFT,
                fields -> RuleChecks.eq(b, fields[0], fields[1]));
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProjectSelect test...");
        runTest();
    }
}
