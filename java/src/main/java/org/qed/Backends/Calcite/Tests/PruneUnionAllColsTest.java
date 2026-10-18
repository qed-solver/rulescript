package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.RRuleInstances.PruneUnionAllCols;
import org.qed.RelType;

import java.util.List;

public final class PruneUnionAllColsTest {
    public static void runTest() {
        var rule = new PruneUnionAllCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneUnionAllCols rule) {
        var schema = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(schema, 3, false);
        var rightName = RuleChecks.table(schema, 3, false);
        var left = schema.transform(org.qed.Backends.Calcite.CalciteRuntime::configure);
        var g = schema.genericProjectionOp("G", new RelType.VarType("G_Type", true));
        left.scan(leftName).scan(rightName).union(true, 2);
        RelNode before = left.project(List.of(left.call(g, left.field(0), left.field(2)))).build();

        var expected = schema.transform(org.qed.Backends.Calcite.CalciteRuntime::configure);
        expected.scan(leftName).project(List.of(expected.field(0), expected.field(2)));
        expected.scan(rightName).project(List.of(expected.field(0), expected.field(2)));
        expected.union(true, 2);
        RelNode after = expected.project(List.of(expected.call(g, expected.field(0), expected.field(1)))).build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneUnionAllCols test...");
        runTest();
    }
}
