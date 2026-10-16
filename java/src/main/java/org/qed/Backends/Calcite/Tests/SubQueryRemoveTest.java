package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SubQueryRemove;
import org.qed.RuleBuilder;

public class SubQueryRemoveTest {

    public static void runTest() {
        var rule = new SubQueryRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SubQueryRemove rule) {
        var base = RuleBuilder.create();
        var r = RuleChecks.table(base, 1, false);
        var s = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var outer = b.scan(r).build();
        var inner = b.scan(s).build();
        var before = LogicalFilter.create(outer, RexSubQuery.exists(inner));
        var probes = b.push(inner).project(b.literal(true)).aggregate(b.groupKey(0)).build();
        var after = b.push(outer).push(probes).join(JoinRelType.INNER, b.literal(true)).project(b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, LogicalFilter.create(outer, b.call(SqlStdOperatorTable.NOT, RexSubQuery.exists(inner))));
        RuleChecks.doesNotApply(rule, b.push(outer).filter(RuleChecks.gt(b, b.field(0), 0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SubQueryRemove test...");
        runTest();
    }
}
