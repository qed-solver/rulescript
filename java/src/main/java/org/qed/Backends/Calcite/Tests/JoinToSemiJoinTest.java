package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinToSemiJoin;
import org.qed.RuleBuilder;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class JoinToSemiJoinTest {
    public static void runTest() {
        var rule = new JoinToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToSemiJoin rule) {
        var qed = RuleBuilder.create();
        var leftName = table(qed, 1, false, false);
        var rightName = table(qed, 1, false, false);
        var b = qed.transform(CalciteRuntime::configure);
        b.scan(leftName);
        var left = b.build();
        b.scan(rightName);
        var rawRight = b.build();
        var aggregate = LogicalAggregate.create(rawRight, List.of(), ImmutableBitSet.of(0), null, List.of());
        var join = LogicalJoin.create(left, aggregate, List.of(), eq(b, b.getRexBuilder().makeInputRef(left, 0),
                b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 1)),
                java.util.Set.of(), JoinRelType.INNER);
        var before = LogicalProject.create(join, List.of(), List.of(b.getRexBuilder().makeInputRef(join, 0)),
                List.of("c0"));

        var semiJoin = LogicalJoin.create(left, rawRight, List.of(), eq(b,
                b.getRexBuilder().makeInputRef(left, 0),
                b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 1)),
                java.util.Set.of(), JoinRelType.SEMI);
        var after = LogicalProject.create(semiJoin, List.of(),
                List.of(b.getRexBuilder().makeCast(left.getRowType().getFieldList().get(0).getType(),
                        b.getRexBuilder().makeInputRef(semiJoin, 0))), List.of("c0"));
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = LogicalJoin.create(left, aggregate, List.of(), eq(b,
                b.getRexBuilder().makeInputRef(left, 0),
                b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 1)),
                java.util.Set.of(), JoinRelType.LEFT);
        var projectedNearMiss = LogicalProject.create(nearMiss, List.of(),
                List.of(b.getRexBuilder().makeInputRef(nearMiss, 0)), List.of("c0"));
        RuleChecks.doesNotApply(rule, projectedNearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
