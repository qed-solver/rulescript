package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PruneValuesCols;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

import java.util.List;

public final class PruneValuesColsTest {
    public static void runTest() {
        var rule = new PruneValuesCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneValuesCols rule) {
        var values = new LiteralValues(new Object[]{1, 2, 3});
        var top = org.qed.RuleBuilder.create().genericProjectionOp("Top", new RelType.VarType("Top_Type", true));
        RelRN beforePattern = values.project(Seq.of(
                new RexRN.Proj(top, Seq.of(values.field(0))), values.field(1)));
        RelNode before = FaithfulPlans.of(beforePattern);

        var tableSchema = org.qed.RuleBuilder.create();
        var tableName = RuleChecks.table(tableSchema, 3, false);
        var beforeBuilder = tableSchema.transform(CalciteRuntime::configure);
        RelNode nearMiss = beforeBuilder.scan(tableName)
                .project(List.of(beforeBuilder.call(top, beforeBuilder.field(0)), beforeBuilder.field(1))).build();
        RuleChecks.doesNotApply(rule, nearMiss);

        var afterValues = new LiteralValues(new Object[]{1, 2});
        RelNode after = FaithfulPlans.expected(afterValues.project(Seq.of(
                new RexRN.Proj(top, Seq.of(afterValues.field(0))), afterValues.field(1))));
        RuleChecks.rewrites(rule, before, after);
    }

    private record LiteralValues(Object[] rowValues) implements RelRN {
        @Override
        public RelNode semantics() {
            var fields = new String[rowValues.length];
            for (var i = 0; i < rowValues.length; i++) fields[i] = "c" + i;
            return org.qed.RuleBuilder.create().values(fields, rowValues).build();
        }
    }

    public static void main(String[] args) {
        System.out.println("Running PruneValuesCols test...");
        runTest();
    }
}
