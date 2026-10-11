package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinPushThroughJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinPushThroughJoinTest {

    public static void runTest() {
        var rule = new JoinPushThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinPushThroughJoin rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var tree = (java.util.function.Function<JoinRelType, RelBuilder>) top -> {
            b.scan(ta).scan(tb).join(JoinRelType.INNER,
                    b.and(RuleChecks.gt(b, b.field(2, 0, 0), 1), RuleChecks.gt(b, b.field(2, 1, 0), 2)));
            return b.scan(tc).join(top, b.and(RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 0, 1)),
                    RuleChecks.gt(b, b.field(2, 1, 0), 3)));
        };
        var before = tree.apply(JoinRelType.INNER).build();

        b.scan(ta).scan(tc).join(JoinRelType.INNER,
                b.and(RuleChecks.gt(b, b.field(2, 0, 0), 1), RuleChecks.gt(b, b.field(2, 1, 0), 3)));
        b.scan(tb).join(JoinRelType.INNER, b.and(RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                RuleChecks.gt(b, b.field(2, 1, 0), 2)));
        var after = b.project(b.field(0), b.field(2), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, tree.apply(JoinRelType.LEFT).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinPushThroughJoin test...");
        runTest();
    }
}
