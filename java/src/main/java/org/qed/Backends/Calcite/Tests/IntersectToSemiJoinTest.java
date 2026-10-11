package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.IntersectToSemiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class IntersectToSemiJoinTest {

    public static void runTest() {
        var rule = new IntersectToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(IntersectToSemiJoin rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(ta).scan(tb).intersect(false, 2).build();
        var after = b.scan(ta).scan(tb)
                .join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b.field(2, 0, 0),
                        b.field(2, 1, 0)))
                .aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).intersect(true, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).minus(false, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running IntersectToSemiJoin test...");
        runTest();
    }
}
