package org.qed.Backends.Calcite.Tests;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.DetectJoinContradiction;
import org.qed.RuleBuilder;

public class DetectJoinContradictionTest {

    public static void runTest() {
        var rule = new DetectJoinContradiction();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RelNode join(RelBuilder b, String l, String r, JoinRelType type,
                                Function<RelBuilder, RexNode> condition) {
        b.scan(l).scan(r);
        var cond = condition.apply(b);
        var right = b.build();
        var left = b.build();
        return LogicalJoin.create(left, right, List.of(), cond, Set.of(), type);
    }

    private static void ordinaryTables(DetectJoinContradiction rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = join(b, l, r, JoinRelType.INNER, x -> x.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                x.literal(false), RuleChecks.eq(x, x.field(2, 0, 0), x.field(2, 1, 0))));
        var after = join(b, l, r, JoinRelType.INNER, x -> x.literal(false));
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = join(b, l, r, JoinRelType.LEFT, x -> x.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                x.literal(false), RuleChecks.eq(x, x.field(2, 0, 0), x.field(2, 1, 0))));
        RuleChecks.doesNotApply(rule, leftJoin);

        var noContradiction = join(b, l, r, JoinRelType.INNER, x -> x.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                RuleChecks.gt(x, x.field(2, 0, 0), 5),
                RuleChecks.eq(x, x.field(2, 0, 0), x.field(2, 1, 0))));
        RuleChecks.doesNotApply(rule, noContradiction);

        var soleFalse = join(b, l, r, JoinRelType.INNER, x -> x.literal(false));
        RuleChecks.doesNotApply(rule, soleFalse);
    }

    public static void main(String[] args) {
        System.out.println("Running DetectJoinContradiction test...");
        runTest();
    }
}
