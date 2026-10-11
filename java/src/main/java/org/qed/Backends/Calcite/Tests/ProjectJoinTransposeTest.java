package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectJoinTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectJoinTransposeTest {

    public static void runTest() {
        var rule = new ProjectJoinTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectJoinTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var onSides = (java.util.function.BiFunction<JoinRelType, Integer, RelBuilder>) (type, offset) -> {
            b.scan(l).scan(r);
            var tl = b.call(SqlStdOperatorTable.PLUS, b.field(2, 0, 0), b.literal(1));
            var tr = b.call(SqlStdOperatorTable.MULTIPLY, b.field(2, 1, 0), b.literal(2));
            b.join(type, b.call(SqlStdOperatorTable.LESS_THAN, tl, tr));
            var top = b.call(SqlStdOperatorTable.PLUS, b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(offset)),
                    b.call(SqlStdOperatorTable.MULTIPLY, b.field(1), b.literal(2)));
            return b.project(top);
        };
        var before = onSides.apply(JoinRelType.INNER, 1).build();

        b.scan(l).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)));
        b.scan(r).project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)));
        b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0)));
        var after = b.project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, onSides.apply(JoinRelType.LEFT, 1).build());
        RuleChecks.doesNotApply(rule, onSides.apply(JoinRelType.INNER, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectJoinTranspose test...");
        runTest();
    }
}
