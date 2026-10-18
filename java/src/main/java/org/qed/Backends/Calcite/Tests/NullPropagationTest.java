package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRule;
import org.qed.RRuleInstances.NullPropagation;
import org.qed.RelType;

public class NullPropagationTest {
    public static void runTest() {
        var rule = new NullPropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var beforeBuilder = builder(false);
        var beforeInput = beforeBuilder.build();
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(beforeInput,
                beforeInput.getCluster().getRexBuilder().makeCall(SqlStdOperatorTable.IS_NULL,
                        beforeInput.getCluster().getRexBuilder().makeInputRef(beforeInput, 0)));
        var afterBuilder = builder(false);
        var afterInput = afterBuilder.build();
        var after = org.apache.calcite.rel.logical.LogicalFilter.create(afterInput,
                afterInput.getCluster().getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(rule, before, after);

        var nullable = builder(true);
        var nullableInput = nullable.build();
        RuleChecks.doesNotApply(rule, org.apache.calcite.rel.logical.LogicalFilter.create(nullableInput,
                nullableInput.getCluster().getRexBuilder().makeCall(SqlStdOperatorTable.IS_NULL,
                        nullableInput.getCluster().getRexBuilder().makeInputRef(nullableInput, 0))));
    }

    private static org.qed.RuleBuilder builder(boolean nullable) {
        var builder = org.qed.RuleBuilder.create();
        var table = builder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(new RelType.VarType("V", nullable), false)));
        builder.addTable(table);
        builder.scan(table.getName());
        return builder;
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
