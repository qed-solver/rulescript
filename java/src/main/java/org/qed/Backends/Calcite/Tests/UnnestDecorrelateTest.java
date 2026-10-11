package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.UnnestDecorrelate;
import org.qed.RuleBuilder;

import java.util.List;

public class UnnestDecorrelateTest {

    public static void runTest() {
        var rule = new UnnestDecorrelate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(UnnestDecorrelate rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var e = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var unnested = (java.util.function.Function<JoinRelType, RelBuilder>) type -> b.scan(l).scan(e)
                .join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = unnested.apply(JoinRelType.INNER)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(2), b.literal(1))).build();
        var after = b.scan(l).project(b.field(0)).scan(e)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, unnested.apply(JoinRelType.LEFT)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(2), b.literal(1))).build());
        RuleChecks.doesNotApply(rule, unnested.apply(JoinRelType.INNER)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running UnnestDecorrelate test...");
        runTest();
    }
}
