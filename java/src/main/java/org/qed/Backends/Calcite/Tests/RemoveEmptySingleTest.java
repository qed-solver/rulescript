package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import com.google.common.collect.ImmutableList;
import org.qed.RuleBuilder;

public class RemoveEmptySingleTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.RemoveEmptySingle();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.RemoveEmptySingle rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var input = builder.scan(table).build();
        var plan = LogicalValues.create(builder.getCluster(), input.getRowType(), ImmutableList.of());
        var grouped = builder.push(plan).aggregate(builder.groupKey(builder.field(0)),
                builder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "sum",
                        java.util.List.of(builder.field(0)))).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = LogicalValues.create(plain.getCluster(), grouped.getRowType(), ImmutableList.of());
        RuleChecks.rewrites(rule, grouped, expected);

        var total = builder.push(plan).aggregate(builder.groupKey(),
                builder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "sum",
                        java.util.List.of(builder.field(0)))).build();
        RuleChecks.doesNotApply(rule, total);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveEmptySingle test...");
        runTest();
    }
}
