package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.SemiJoinProjectTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class SemiJoinProjectTransposeTest {

    public static void runTest() {
        var rule = new SemiJoinProjectTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SemiJoinProjectTranspose rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 2, false);
        var y = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var projected = (java.util.function.Function<JoinRelType, RelBuilder>) type -> {
            b.scan(x).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                    b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1)));
            return b.scan(y).join(type, b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0)));
        };
        var before = projected.apply(JoinRelType.SEMI).build();

        b.scan(x).scan(y).join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.LESS_THAN,
                b.call(SqlStdOperatorTable.PLUS, b.field(2, 0, 0), b.field(2, 0, 1)), b.field(2, 1, 0)));
        var after = b.project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, projected.apply(JoinRelType.ANTI).build());
        RuleChecks.doesNotApply(rule, projected.apply(JoinRelType.INNER).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinProjectTranspose test...");
        runTest();
    }
}
