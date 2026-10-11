package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.SemiJoinJoinTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class SemiJoinJoinTransposeTest {

    public static void runTest() {
        var rule = new SemiJoinJoinTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SemiJoinJoinTranspose rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 1, false);
        var y = RuleChecks.table(base, 1, false);
        var z = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var tree = (java.util.function.BiFunction<JoinRelType, Integer, RelBuilder>) (lower, semiLeftColumn) -> {
            b.scan(x).scan(y).join(lower, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
            return b.scan(z).join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.LESS_THAN,
                    b.field(2, 0, semiLeftColumn), b.field(2, 1, 0)));
        };
        var before = tree.apply(JoinRelType.INNER, 0).build();

        b.scan(x).scan(z).join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0),
                b.field(2, 1, 0)));
        var after = b.scan(y).join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, tree.apply(JoinRelType.INNER, 1).build());
        RuleChecks.doesNotApply(rule, tree.apply(JoinRelType.LEFT, 0).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinJoinTranspose test...");
        runTest();
    }
}
