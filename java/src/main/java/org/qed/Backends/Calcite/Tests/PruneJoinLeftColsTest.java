package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PruneJoinLeftCols;
import org.qed.RuleBuilder;

public class PruneJoinLeftColsTest {

    public static void runTest() {
        var rule = new PruneJoinLeftCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneJoinLeftCols rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 1, false);
        var wide = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var joinOn = (java.util.function.Function<String, RelBuilder>) left -> {
            b.scan(left).scan(r);
            b.join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
            return b;
        };
        var before = joinOn.apply(l)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(2))).build();
        var after = b.scan(l).project(b.field(0)).scan(r)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(2))).build());
        RuleChecks.doesNotApply(rule, joinOn.apply(wide)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(3))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneJoinLeftCols test...");
        runTest();
    }
}
