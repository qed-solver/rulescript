package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.MultiJoinProjectTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class MultiJoinProjectTransposeTest {

    public static void runTest() {
        var rule = new MultiJoinProjectTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MultiJoinProjectTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var projected = (java.util.function.Function<JoinRelType, RelBuilder>) type -> {
            b.scan(l).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).scan(r);
            return b.join(type, b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0)));
        };
        var before = projected.apply(JoinRelType.INNER).build();

        b.scan(l).scan(r).join(JoinRelType.INNER, b.call(SqlStdOperatorTable.LESS_THAN,
                b.call(SqlStdOperatorTable.PLUS, b.field(2, 0, 0), b.field(2, 0, 1)), b.field(2, 1, 0)));
        var after = b.project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(2), b.field(3))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, projected.apply(JoinRelType.LEFT).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running MultiJoinProjectTranspose test...");
        runTest();
    }
}
