package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.SqlOperator;
import com.google.common.collect.ImmutableList;
import org.qed.RRuleInstances.PruneWithCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneWithColsTest {
    public static void runTest() {
        var rule = new PruneWithCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWithCols rule) {
        var beforeBuilder = RuleBuilder.create();
        var beforeQedTable = beforeBuilder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(new RelType.VarType("Input_Type", true), false)));
        var beforeTable = beforeBuilder.addTable(beforeQedTable).getClass() == null ? "" : beforeQedTable.getName();
        var scan = beforeBuilder.scan(beforeTable).build();
        var beforeInput = LogicalProject.create(scan, ImmutableList.of(), ImmutableList.of(
                beforeBuilder.getRexBuilder().makeInputRef(scan, 0)), ImmutableList.of("With_Column_0"));
        SqlOperator output = beforeBuilder.genericProjectionOp("OrdinaryWithOutput",
                new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.INTEGER, true));
        RelNode before = LogicalProject.create(beforeInput, ImmutableList.of(), ImmutableList.of(
                beforeBuilder.getRexBuilder().makeCall(output,
                        beforeBuilder.getRexBuilder().makeInputRef(beforeInput, 0)),
                beforeBuilder.getRexBuilder().makeInputRef(beforeInput, 0)), ImmutableList.of("$f0", "$f1"));

        var expectedBuilder = RuleBuilder.create();
        var expectedQedTable = expectedBuilder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(new RelType.VarType("Input_Type", true), false)));
        expectedBuilder.addTable(expectedQedTable);
        var expectedTable = expectedQedTable.getName();
        var expectedScan = expectedBuilder.scan(expectedTable).build();
        var expectedWith = LogicalProject.create(expectedScan, ImmutableList.of(), ImmutableList.of(
                expectedBuilder.getRexBuilder().makeInputRef(expectedScan, 0)), ImmutableList.of("With_Column_0"));
        RelNode after = LogicalProject.create(expectedScan, ImmutableList.of(), ImmutableList.of(
                expectedBuilder.getRexBuilder().makeCall(output,
                        expectedBuilder.getRexBuilder().makeInputRef(expectedScan, 0)),
                expectedBuilder.getRexBuilder().makeInputRef(expectedScan, 0)), ImmutableList.of("$f0", "$f1"));
        RuleChecks.rewrites(rule, before, after);

        var missBuilder = RuleBuilder.create();
        var missQedTable = missBuilder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(new RelType.VarType("Input_Type", true), false)));
        missBuilder.addTable(missQedTable);
        var missTable = missQedTable.getName();
        var missScan = missBuilder.scan(missTable).build();
        var missInput = LogicalProject.create(missScan, ImmutableList.of(), ImmutableList.of(
                missBuilder.getRexBuilder().makeInputRef(missScan, 0)), ImmutableList.of("With_Column_0"));
        var wrongOutput = missBuilder.genericProjectionOp("OrdinaryWithOutput",
                new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.INTEGER, true));
        RuleChecks.doesNotApply(rule, LogicalProject.create(missInput, ImmutableList.of(), ImmutableList.of(
                missBuilder.getRexBuilder().makeCall(wrongOutput,
                        missBuilder.getRexBuilder().makeInputRef(missInput, 0)),
                missBuilder.getRexBuilder().makeCall(wrongOutput,
                        missBuilder.getRexBuilder().makeInputRef(missInput, 0))), ImmutableList.of("$f0", "$f1")));
        System.out.println("succeeded (ordinary table and swapped-column near miss)");
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
