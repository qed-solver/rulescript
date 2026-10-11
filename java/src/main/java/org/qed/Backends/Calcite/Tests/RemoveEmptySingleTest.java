package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.RemoveEmptySingle;
import org.qed.RuleBuilder;

import java.util.List;

public class RemoveEmptySingleTest {

    public static void runTest() {
        var rule = new RemoveEmptySingle();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveEmptySingle rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var keepValues = b.transform(config -> config.withSimplifyValues(false));
        var emptyInput = (java.util.function.Supplier<RelBuilder>) () -> keepValues.scan(t).empty();
        var before = emptyInput.get().aggregate(keepValues.groupKey(0),
                keepValues.aggregateCall(SqlStdOperatorTable.SUM, keepValues.field(0))).build();
        var after = b.push(before).empty().build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, keepValues.scan(t).aggregate(keepValues.groupKey(0),
                keepValues.aggregateCall(SqlStdOperatorTable.SUM, keepValues.field(0))).build());
        RuleChecks.doesNotApply(rule, emptyInput.get().aggregate(keepValues.groupKey(),
                keepValues.aggregateCall(SqlStdOperatorTable.COUNT, keepValues.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveEmptySingle test...");
        runTest();
    }
}
