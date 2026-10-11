package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinConditionPush;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinConditionPushTest {

    public static void runTest() {
        var rule = new JoinConditionPush();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinConditionPush rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.BiFunction<JoinRelType, Boolean, RelBuilder>) (type, withRightOnly) -> {
            b.scan(l).scan(r);
            return b.join(type, withRightOnly
                    ? b.and(RuleChecks.gt(b, b.field(2, 0, 0), 5), RuleChecks.gt(b, b.field(2, 1, 0), 6),
                            RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                    : b.and(RuleChecks.gt(b, b.field(2, 0, 0), 5), RuleChecks.eq(b, b.field(2, 0, 0),
                            b.field(2, 1, 0))));
        };
        var before = join.apply(JoinRelType.INNER, true).build();
        b.scan(l).filter(RuleChecks.gt(b, b.field(0), 5)).scan(r).filter(RuleChecks.gt(b, b.field(0), 6));
        var after = b.join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.LEFT, true).build());
        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.INNER, false).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinConditionPush test...");
        runTest();
    }
}
