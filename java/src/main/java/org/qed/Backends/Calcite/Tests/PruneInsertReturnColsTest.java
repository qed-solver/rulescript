package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.RRuleInstances.PruneInsertReturnCols;

import java.util.List;

public class PruneInsertReturnColsTest {
    public static void runTest() {
        var rule = new PruneInsertReturnCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneInsertReturnCols rule) {
        var ordinaryBuilder = org.qed.RuleBuilder.create();
        var tableName = RuleChecks.table(ordinaryBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ordinaryBuilder);
        var scan = builder.scan(tableName).build();
        var rex = builder.getRexBuilder();
        var input = org.apache.calcite.rex.RexInputRef.of(0, scan.getRowType());
        var kept = rex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Ret", new org.qed.RelType.VarType("Ret_Type", true)), input);
        var dropped = rex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Drop", new org.qed.RelType.VarType("Drop_Type", true)), input);
        RelNode insert = project(scan, List.of(input, kept, dropped), List.of("key", "ret", "drop"));
        var top = rex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true)), input, rex.makeInputRef(insert.getRowType().getFieldList().get(1).getType(), 1));
        RelNode before = project(insert, List.of(top), List.of("top"));

        var expectedOrdinaryBuilder = org.qed.RuleBuilder.create();
        var expectedTableName = RuleChecks.table(expectedOrdinaryBuilder, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedOrdinaryBuilder);
        var expectedScan = expectedBuilder.scan(expectedTableName).build();
        var expectedRex = expectedBuilder.getRexBuilder();
        var expectedInput = org.apache.calcite.rex.RexInputRef.of(0, expectedScan.getRowType());
        var expectedKept = expectedRex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Ret", new org.qed.RelType.VarType("Ret_Type", true)), expectedInput);
        RelNode expectedInsert = project(expectedScan, List.of(expectedInput, expectedKept), List.of("key", "ret"));
        var expectedTop = expectedRex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true)), expectedInput, expectedRex.makeInputRef(expectedInsert.getRowType().getFieldList().get(1).getType(), 1));
        RelNode after = project(expectedInsert, List.of(expectedTop), List.of("top"));
        RuleChecks.rewrites(rule, before, after);

        var nearOrdinaryBuilder = org.qed.RuleBuilder.create();
        var nearTableName = RuleChecks.table(nearOrdinaryBuilder, 1, false);
        var nearBuilder = RuleChecks.ruleBuilder(nearOrdinaryBuilder);
        var nearScan = nearBuilder.scan(nearTableName).build();
        var nearRex = nearBuilder.getRexBuilder();
        var nearInput = org.apache.calcite.rex.RexInputRef.of(0, nearScan.getRowType());
        var nearKept = nearRex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Ret", new org.qed.RelType.VarType("Ret_Type", true)), nearInput);
        var nearDropped = nearRex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Drop", new org.qed.RelType.VarType("Drop_Type", true)), nearInput);
        RelNode nearInsert = project(nearScan, List.of(nearInput, nearKept, nearDropped), List.of("key", "ret", "drop"));
        var wrongOutput = nearRex.makeCall(org.qed.RuleBuilder.create().genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true)), nearInput, nearRex.makeInputRef(nearInsert.getRowType().getFieldList().get(2).getType(), 2));
        RelNode nearMiss = project(nearInsert, List.of(wrongOutput), List.of("top"));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode project(RelNode input, List<org.apache.calcite.rex.RexNode> expressions, List<String> names) {
        var builder = RuleChecks.ruleBuilder(org.qed.RuleBuilder.create());
        builder.push(input);
        builder.project(expressions, names, true);
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
