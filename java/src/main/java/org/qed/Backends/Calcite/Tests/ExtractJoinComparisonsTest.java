package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ExtractJoinComparisons;
import org.qed.RuleBuilder;

public class ExtractJoinComparisonsTest {

    public static void runTest() {
        var rule = new ExtractJoinComparisons();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RexNode cond(RelBuilder b) {
        return b.call(SqlStdOperatorTable.GREATER_THAN,
                b.call(SqlStdOperatorTable.PLUS, b.field(2, 0, 0), b.literal(1)),
                b.call(SqlStdOperatorTable.MULTIPLY, b.field(2, 1, 0), b.literal(2)));
    }

    private static void ordinaryTables(ExtractJoinComparisons rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var wide = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(l).scan(r).join(JoinRelType.INNER, cond(b)).build();

        var lp = b.scan(l).project(b.field(0), b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).build();
        var rp = b.scan(r).project(b.field(0), b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2))).build();
        var after = b.push(lp).push(rp)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 1), b.field(2, 1, 1)))
                .project(b.field(0), b.field(2))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).join(JoinRelType.LEFT, cond(b)).build());
        RuleChecks.doesNotApply(rule, b.scan(wide).scan(r).join(JoinRelType.INNER, cond(b)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractJoinComparisons test...");
        runTest();
    }
}
