package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ExpandDisjunctionForJoinInputs;
import org.qed.RuleBuilder;

import java.util.List;

public class ExpandDisjunctionForJoinInputsTest {

    public static void runTest() {
        var rule = new ExpandDisjunctionForJoinInputs();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ExpandDisjunctionForJoinInputs rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.BiFunction<JoinRelType, java.util.function.Function<RelBuilder, RexNode>,
                RelBuilder>) (type, cond) -> b.scan(l).scan(r).join(type, cond.apply(b));
        java.util.function.Function<RelBuilder, RexNode> disjunction = x -> x.or(
                x.and(RuleChecks.gt(x, x.field(2, 0, 0), 1), RuleChecks.gt(x, x.field(2, 1, 0), 3)),
                x.and(RuleChecks.gt(x, x.field(2, 0, 1), 2), RuleChecks.gt(x, x.field(2, 1, 1), 4)));
        var before = join.apply(JoinRelType.INNER, disjunction).build();

        b.scan(l).scan(r);
        var after = b.join(JoinRelType.INNER, b.and(disjunction.apply(b),
                b.or(RuleChecks.gt(b, b.field(2, 0, 0), 1), RuleChecks.gt(b, b.field(2, 0, 1), 2)),
                b.or(RuleChecks.gt(b, b.field(2, 1, 0), 3), RuleChecks.gt(b, b.field(2, 1, 1), 4)))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.LEFT, disjunction).build());
        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.INNER,
                x -> x.and(RuleChecks.gt(x, x.field(2, 0, 0), 1), RuleChecks.gt(x, x.field(2, 1, 0), 3))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ExpandDisjunctionForJoinInputs test...");
        runTest();
    }
}
