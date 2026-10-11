package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PruneJoinRightCols;
import org.qed.RuleBuilder;

public class PruneJoinRightColsTest {

    public static void runTest() {
        var rule = new PruneJoinRightCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneJoinRightCols rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 2, false);
        var narrow = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = join(b, l, r, JoinRelType.INNER)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build();
        b.scan(l).scan(r).project(b.field(0));
        var after = b.join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join(b, l, r, JoinRelType.LEFT)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build());
        RuleChecks.doesNotApply(rule, join(b, l, narrow, JoinRelType.INNER)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build());
    }

    private static RelBuilder join(RelBuilder b, String left, String right, JoinRelType type) {
        return b.scan(left).scan(right).join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
    }

    public static void main(String[] args) {
        System.out.println("Running PruneJoinRightCols test...");
        runTest();
    }
}
