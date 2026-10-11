package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushLeakproofJoinIntoPermeableBarrierLeft;
import org.qed.RuleBuilder;

import java.util.List;

public class PushLeakproofJoinIntoPermeableBarrierLeftTest {

    public static void runTest() {
        var rule = new PushLeakproofJoinIntoPermeableBarrierLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushLeakproofJoinIntoPermeableBarrierLeft rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).project(List.of(b.field(0), b.field(1)), List.of(), true)
                .scan(r)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(base);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .project(plain.field(0), plain.field(1), plain.field(2), plain.field(3))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).project(b.field(1), b.field(0))
                .scan(r)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(l).project(b.field(0), b.field(1))
                .scan(r)
                .join(JoinRelType.LEFT, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofJoinIntoPermeableBarrierLeft test...");
        runTest();
    }
}
