package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PruneScanCols;
import org.qed.RuleBuilder;

public class PruneScanColsTest {
    public static void main(String[] args) {
        runTest();
    }

    public static void runTest() {
        System.out.println("Running RULE test...");
        var rule = new PruneScanCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneScanCols rule) {
        var sourceBuilder = RuleBuilder.create();
        var table = RuleChecks.table(sourceBuilder, 3, false);
        var b = RuleChecks.ruleBuilder(sourceBuilder);
        b.scan(table);
        var scan = b.build();
        var top = sourceBuilder.genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true));
        var before = b.push(scan).project(b.call(top, b.field(0), b.field(1))).build();
        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 3, false);
        var expected = RuleChecks.ruleBuilder(expectedBuilder);
        expected.scan(expectedTable);
        RelNode sourceAfterPrune = expected.build();
        sourceAfterPrune = expected.push(sourceAfterPrune).project(expected.field(0), expected.field(1)).build();
        var expectedTop = expectedBuilder.genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true));
        RelNode pruned = expected.push(sourceAfterPrune)
                .project(expected.call(expectedTop, expected.field(0), expected.field(1))).build();
        RuleChecks.rewrites(rule, before, pruned);
        var nearMissBuilder = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissBuilder, 4, false);
        var nearMiss = RuleChecks.ruleBuilder(nearMissBuilder);
        nearMiss.scan(nearMissTable);
        var plan = nearMiss.build();
        var nearTop = nearMissBuilder.genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true));
        plan = nearMiss.push(plan).project(nearMiss.call(nearTop, nearMiss.field(0), nearMiss.field(1))).build();
        RuleChecks.doesNotApply(rule, plan);
    }
}
