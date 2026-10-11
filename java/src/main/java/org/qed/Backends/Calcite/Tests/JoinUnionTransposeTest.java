package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinUnionTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinUnionTransposeTest {

    public static void runTest() {
        var rule = new JoinUnionTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinUnionTranspose rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 1, false);
        var y = RuleChecks.table(base, 1, false);
        var o = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cond = (java.util.function.Function<RelBuilder, RexNode>) q ->
                q.call(SqlStdOperatorTable.LESS_THAN, q.field(2, 0, 0), q.field(2, 1, 0));
        var before = b.scan(x).scan(y).union(true, 2).scan(o).join(JoinRelType.INNER, cond.apply(b)).build();
        b.scan(x).scan(o).join(JoinRelType.INNER, cond.apply(b));
        b.scan(y).scan(o).join(JoinRelType.INNER, cond.apply(b));
        var after = b.union(true, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule,
                b.scan(x).scan(y).union(false, 2).scan(o).join(JoinRelType.INNER, cond.apply(b)).build());
        RuleChecks.doesNotApply(rule,
                b.scan(o).scan(x).scan(y).union(true, 2).join(JoinRelType.INNER, cond.apply(b)).build());
        RuleChecks.doesNotApply(rule,
                b.scan(x).scan(y).union(true, 2).scan(o).join(JoinRelType.LEFT, cond.apply(b)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinUnionTranspose test...");
        runTest();
    }
}
