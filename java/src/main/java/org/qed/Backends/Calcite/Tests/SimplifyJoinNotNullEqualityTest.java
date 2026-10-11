package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.SimplifyJoinNotNullEquality;
import org.qed.RuleBuilder;

public class SimplifyJoinNotNullEqualityTest {

    public static void runTest() {
        var rule = new SimplifyJoinNotNullEquality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RexNode isNotFalseEq(RelBuilder b) {
        return b.call(SqlStdOperatorTable.IS_NOT_FALSE,
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
    }

    private static RexNode eq(RelBuilder b) {
        return RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0));
    }

    private static RexNode rest(RelBuilder b) {
        return RuleChecks.gt(b, b.field(2, 0, 0), 5);
    }

    private static void ordinaryTables(SimplifyJoinNotNullEquality rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false, false);
        var r = RuleChecks.table(base, 1, false, false);
        var nullable = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.and(isNotFalseEq(b), rest(b)))
                .build();
        var after = b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.and(eq(b), rest(b)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(nullable).scan(r)
                .join(JoinRelType.INNER, b.and(isNotFalseEq(b), rest(b))).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.LEFT, b.and(isNotFalseEq(b), rest(b))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyJoinNotNullEquality test...");
        runTest();
    }
}
