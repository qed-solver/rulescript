package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinToCorrelate;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinToCorrelateTest {

    public static void runTest() {
        var rule = new JoinToCorrelate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToCorrelate rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r).join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();

        var freshBase = RuleBuilder.create();
        RuleChecks.table(freshBase, 1, false);
        RuleChecks.table(freshBase, 1, false);
        var fresh = RuleChecks.ruleBuilder(freshBase);
        var after = RuleChecks.correlate(fresh, fresh.scan(l).build(), fresh.scan(r).build(), JoinRelType.INNER,
                args -> fresh.call(SqlStdOperatorTable.EQUALS, args[0], args[1]));
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinToCorrelate test...");
        runTest();
    }
}
