package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateJoinJoinRemove;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateJoinJoinRemoveTest {

    public static void runTest() {
        var rule = new AggregateJoinJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateJoinJoinRemove rule) {
        var base = RuleBuilder.create();
        var a = RuleChecks.table(base, 1, false);
        var bb = RuleChecks.table(base, 1, false);
        var c = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(a).scan(bb).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = b.scan(c).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0, 2)).build();
        var after = b.scan(a).scan(c).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0, 1)).build();
        RuleChecks.rewrites(rule, before, after);

        b.scan(a).scan(bb).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        RuleChecks.doesNotApply(rule, b.scan(c)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0, 2)).build());
        b.scan(a).scan(bb).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        RuleChecks.doesNotApply(rule, b.scan(c)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(1, 2)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateJoinJoinRemove test...");
        runTest();
    }
}
