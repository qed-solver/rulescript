package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.IntersectToExists;
import org.qed.RuleBuilder;

import java.util.List;

public class IntersectToExistsTest {

    public static void runTest() {
        var rule = new IntersectToExists();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(IntersectToExists rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(ta).scan(tb).intersect(false, 2).build();

        var freshBase = RuleBuilder.create();
        RuleChecks.table(freshBase, 1, false);
        RuleChecks.table(freshBase, 1, false);
        var fresh = RuleChecks.ruleBuilder(freshBase);
        var left = fresh.scan(ta).build();
        var right = fresh.scan(tb).build();
        var correlate = RuleChecks.correlate(fresh, left, right, JoinRelType.SEMI,
                args -> fresh.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, args[0], args[1]));
        var after = fresh.push(correlate).aggregate(fresh.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).intersect(true, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).minus(false, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running IntersectToExists test...");
        runTest();
    }
}
