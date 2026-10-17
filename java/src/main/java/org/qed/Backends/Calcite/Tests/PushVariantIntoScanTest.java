package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PushVariantIntoScan;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PushVariantIntoScanTest {
    public static void runTest() {
        var rule = new PushVariantIntoScan();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushVariantIntoScan rule) {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 2, false);
        var scan = builder.scan(source).build();
        var variant = scan.getRowType().getFieldList().get(1).getType();
        var aType = RelType.fromString("INTEGER", true);
        var bType = RelType.fromString("INTEGER", true);
        SqlOperator extractA = builder.genericProjectionOp("ExtractA", aType);
        SqlOperator extractB = builder.genericProjectionOp("ExtractB", bType);
        SqlOperator predicate = builder.genericPredicateOp("P", true);
        var rex = builder.getRexBuilder();
        RexNode c = rex.makeInputRef(scan.getRowType().getFieldList().get(0).getType(), 0);
        RexNode v = rex.makeInputRef(variant, 1);
        RexNode a = builder.call(extractA, v);
        RexNode b = builder.call(extractB, v);
        RelNode filtered = LogicalFilter.create(scan, builder.call(predicate, a));
        RelNode plan = LogicalProject.create(filtered, List.of(), List.of(c, a, b, v), List.of("c", "a", "b", "v"));

        var plain = RuleChecks.ruleBuilder(builder);
        var plainScan = plain.push(scan);
        var plainV = plain.field(1);
        var plainA = plain.call(extractA, plainV);
        var plainB = plain.call(extractB, plainV);
        RelNode expectedScan = plain.project(List.of(plain.field(0), plainA, plainB, plainV), List.of("c", "a", "b", "v"), true).build();
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        expectedBuilder.push(expectedScan);
        var expectedA = expectedBuilder.field(1);
        RelNode expectedFilter = LogicalFilter.create(expectedScan, expectedBuilder.call(predicate, expectedA));
        var finalBuilder = RuleChecks.ruleBuilder(builder);
        finalBuilder.push(expectedFilter);
        var expectedFields = List.of(finalBuilder.field(0), finalBuilder.field(1), finalBuilder.field(2), finalBuilder.field(3));
        var casts = new java.util.ArrayList<RexNode>();
        var originalTypes = plan.getRowType().getFieldList();
        for (var i = 0; i < expectedFields.size(); i++) {
            casts.add(finalBuilder.getRexBuilder().makeCast(originalTypes.get(i).getType(), expectedFields.get(i), true));
        }
        RelNode expected = finalBuilder.project(casts, List.of("c", "a", "b", "v"), true).build();
        RuleChecks.rewrites(rule, plan, expected);

        var wrong = RuleBuilder.create();
        var wrongTable = RuleChecks.table(wrong, 2, false);
        var wrongScan = wrong.scan(wrongTable).build();
        SqlOperator otherAOp = wrong.genericProjectionOp("OtherExtract", aType);
        var wrongV = wrong.getRexBuilder().makeInputRef(wrongScan.getRowType().getFieldList().get(1).getType(), 1);
        var wrongA = wrong.call(otherAOp, wrongV);
        var wrongB = wrong.call(wrong.genericProjectionOp("ExtractB", bType), wrong.getRexBuilder().makeInputRef(wrongScan.getRowType().getFieldList().get(0).getType(), 0));
        RelNode wrongFilter = LogicalFilter.create(wrongScan, wrong.call(wrong.genericPredicateOp("P", true), wrongA));
        RelNode nearMiss = LogicalProject.create(wrongFilter, List.of(), List.of(
                wrong.getRexBuilder().makeInputRef(wrongScan.getRowType().getFieldList().get(0).getType(), 0),
                wrongA, wrongB, wrongV), List.of("c", "a", "b", "v"));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushVariantIntoScan test...");
        runTest();
    }
}
