package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class FoldEqTrueTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.FoldEqTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltBooleanEquality();
        preservesHandBuiltNonmatchingEquality();
    }

    private static void rewritesHandBuiltBooleanEquality() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(kala.tuple.Tuple.of(
                new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN, true), false)));
        builder.addTable(table);
        var scan = builder.scan(table.getName()).build();
        var field = builder.push(scan).field(0);
        var equality = builder.call(SqlStdOperatorTable.EQUALS, field, builder.literal(true));
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(scan, equality);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = expectedBuilder.createQedTable(Seq.of(kala.tuple.Tuple.of(
                new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN, true), false)));
        expectedBuilder.addTable(expectedTable);
        var expectedScan = expectedBuilder.scan(expectedTable.getName()).build();
        var expectedField = expectedBuilder.push(expectedScan).field(0);
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(expectedScan, expectedField);
        RuleChecks.rewrites(new org.qed.RRuleInstances.FoldEqTrue(), before, expected);
    }

    private static void preservesHandBuiltNonmatchingEquality() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(kala.tuple.Tuple.of(
                new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN, true), false)));
        builder.addTable(table);
        var scan = builder.scan(table.getName()).build();
        var field = builder.push(scan).field(0);
        var equality = builder.call(SqlStdOperatorTable.EQUALS, field, builder.literal(false));
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(scan, equality);
        RuleChecks.doesNotApply(new org.qed.RRuleInstances.FoldEqTrue(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldEqTrue test...");
        runTest();
    }
}
