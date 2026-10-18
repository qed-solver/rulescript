package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.DphypJoinReorder;
import org.qed.RuleBuilder;

public class DphypJoinReorderTest {
    public static void runTest() {
        var rule = new DphypJoinReorder();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var builder = ruleBuilder();
        var a = table(builder);
        var b = table(builder);
        var c = table(builder);
        var before = before(builder, a, b, c, JoinRelType.INNER);
        var expected = after(builder, a, b, c);
        rewrites(rule, before, expected);
        doesNotApply(rule, before(builder, a, b, c, JoinRelType.LEFT));
    }

    private static RuleBuilder ruleBuilder() {
        return RuleBuilder.create();
    }

    private static String table(RuleBuilder builder) {
        return RuleChecks.table(builder, 1, false);
    }

    private static RelNode before(RelBuilder builder, String a, String b, String c, JoinRelType rootType) {
        var ab = builder.scan(a).scan(b).join(JoinRelType.INNER,
                less(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        return builder.push(ab).scan(c).join(rootType, builder.and(
                less(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                less(builder, builder.field(2, 0, 1), builder.field(2, 1, 0)))).build();
    }

    private static RelNode after(RelBuilder builder, String a, String b, String c) {
        var bc = builder.scan(b).scan(c).join(JoinRelType.INNER,
                less(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        return builder.scan(a).push(bc).join(JoinRelType.INNER, builder.and(
                less(builder, builder.field(2, 0, 0), builder.field(2, 1, 1)),
                less(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))).build();
    }

    private static RexNode less(RelBuilder builder, RexNode left, RexNode right) {
        return builder.call(SqlStdOperatorTable.LESS_THAN, left, right);
    }

    private static void rewrites(DphypJoinReorder rule, RelNode before, RelNode after) {
        RuleChecks.rewrites(rule, before, after);
    }

    private static void doesNotApply(DphypJoinReorder rule, RelNode plan) {
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running DphypJoinReorder test...");
        runTest();
    }
}
