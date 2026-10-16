package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneMutationReturnCols;
import org.qed.RuleBuilder;

public class PruneMutationReturnColsTest {

    public static void runTest() {
        var rule = new PruneMutationReturnCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneMutationReturnCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 4, false);
        var b = RuleChecks.ruleBuilder(base);
        var returned = (java.util.function.Function<Integer, RexNode>) i -> b.call(SqlStdOperatorTable.PLUS,
                b.field(i), b.field((i + 1) % 4));
        var before = b.scan(t).project(returned.apply(0), returned.apply(1), returned.apply(2), returned.apply(3))
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(3)).build();
        var after = b.scan(t).project(returned.apply(0), returned.apply(2), returned.apply(3))
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(2)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(returned.apply(0), returned.apply(1), returned.apply(2), returned.apply(3))
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(1)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneMutationReturnCols test...");
        runTest();
    }
}
