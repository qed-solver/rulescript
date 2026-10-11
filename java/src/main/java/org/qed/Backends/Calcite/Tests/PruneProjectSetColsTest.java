package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PruneProjectSetCols;
import org.qed.RuleBuilder;

public class PruneProjectSetColsTest {

    public static void runTest() {
        var rule = new PruneProjectSetCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneProjectSetCols rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var e = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        SqlOperator m = base.genericPredicateOp("M", true);
        SqlOperator g = base.genericProjectionOp("G", org.qed.RelType.fromString("INTEGER", true));

        var before = b.scan(l).scan(e)
                .join(JoinRelType.INNER, b.call(m, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(g, b.field(0), b.field(2)))
                .build();
        var after = b.scan(l).project(b.field(0)).scan(e)
                .join(JoinRelType.INNER, b.call(m, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(g, b.field(0), b.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(e)
                .join(JoinRelType.LEFT, b.call(m, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(g, b.field(0), b.field(2)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(e)
                .join(JoinRelType.INNER, b.call(m, b.field(2, 0, 1), b.field(2, 1, 0)))
                .project(b.call(g, b.field(0), b.field(2)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneProjectSetCols test...");
        runTest();
    }
}
