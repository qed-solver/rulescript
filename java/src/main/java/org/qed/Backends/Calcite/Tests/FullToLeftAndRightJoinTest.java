package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FullToLeftAndRightJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class FullToLeftAndRightJoinTest {

    public static void runTest() {
        var rule = new FullToLeftAndRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FullToLeftAndRightJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.BiFunction<JoinRelType, org.apache.calcite.sql.SqlOperator, RelBuilder>)
                (type, op) -> b.scan(l).scan(r).join(type, b.call(op, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = join.apply(JoinRelType.FULL, SqlStdOperatorTable.EQUALS).build();

        b.scan(l).scan(r).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        b.scan(l).scan(r).join(JoinRelType.RIGHT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .filter(b.call(SqlStdOperatorTable.IS_NOT_TRUE, RuleChecks.eq(b, b.field(0), b.field(1))));
        var after = b.union(true, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.LEFT, SqlStdOperatorTable.EQUALS).build());
        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.FULL, SqlStdOperatorTable.LESS_THAN).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FullToLeftAndRightJoin test...");
        runTest();
    }
}
