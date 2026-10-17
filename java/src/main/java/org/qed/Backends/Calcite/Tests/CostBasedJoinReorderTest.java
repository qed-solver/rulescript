package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.CostBasedJoinReorder;
import org.qed.RuleBuilder;

public class CostBasedJoinReorderTest {
    public static void runTest() {
        var rule = new CostBasedJoinReorder();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var beforeBuilder = builder();
        var before = beforeBuilder.scan("Table_0").scan("Table_1")
                .join(JoinRelType.INNER, lt(beforeBuilder, 0, 0, 1, 0))
                .scan("Table_2").join(JoinRelType.INNER, beforeBuilder.call(SqlStdOperatorTable.AND,
                        lt(beforeBuilder, 0, 0, 1, 0), lt(beforeBuilder, 0, 1, 1, 0)))
                .scan("Table_3").join(JoinRelType.INNER, lt(beforeBuilder, 0, 2, 1, 0)).build();

        var afterBuilder = builder();
        var after = afterBuilder.scan("Table_0").scan("Table_1")
                .join(JoinRelType.INNER, lt(afterBuilder, 0, 0, 1, 0))
                .scan("Table_2").scan("Table_3").join(JoinRelType.INNER, lt(afterBuilder, 0, 0, 1, 0))
                .join(JoinRelType.INNER, afterBuilder.call(SqlStdOperatorTable.AND,
                        lt(afterBuilder, 0, 0, 1, 0), lt(afterBuilder, 0, 1, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearBuilder = builder();
        var nearMiss = nearBuilder.scan("Table_0").scan("Table_1")
                .join(JoinRelType.INNER, lt(nearBuilder, 0, 0, 1, 0))
                .scan("Table_2").join(JoinRelType.INNER, nearBuilder.call(SqlStdOperatorTable.AND,
                        lt(nearBuilder, 0, 0, 1, 0), lt(nearBuilder, 0, 1, 1, 0)))
                .scan("Table_3").join(JoinRelType.LEFT, lt(nearBuilder, 0, 2, 1, 0)).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RuleBuilder builder() {
        var builder = RuleBuilder.create();
        for (var name : new String[]{"A", "B", "C", "D"}) {
            RuleChecks.table(builder, 1, false);
        }
        return builder;
    }

    private static org.apache.calcite.rex.RexNode lt(RelBuilder builder, int leftInput, int leftField,
                                                     int rightInput, int rightField) {
        return builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(2, leftInput, leftField),
                builder.field(2, rightInput, rightField));
    }

    public static void main(String[] args) {
        System.out.println("Running CostBasedJoinReorder test...");
        runTest();
    }
}
