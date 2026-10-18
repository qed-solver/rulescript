package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ReplaceCTERefWithRepartition;
import org.qed.RuleBuilder;

public class ReplaceCTERefWithRepartitionTest {
    public static void runTest() {
        var rule = new ReplaceCTERefWithRepartition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ReplaceCTERefWithRepartition rule) {
        var qed = RuleBuilder.create();
        var one = RuleChecks.table(qed, 1, false, false);
        var two = RuleChecks.table(qed, 2, false, false);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var scan = b.scan(one).build();
        var filtered = LogicalFilter.create(scan, rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rex.makeInputRef(scan, 0)));
        RuleChecks.rewrites(rule, filtered, filtered);

        RuleChecks.doesNotApply(rule, b.scan(two).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceCTERefWithRepartition test...");
        runTest();
    }
}
