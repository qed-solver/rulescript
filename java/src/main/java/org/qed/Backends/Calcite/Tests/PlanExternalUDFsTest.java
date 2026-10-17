package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RRuleInstances.PlanExternalUDFs;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PlanExternalUDFsTest {
    public static void runTest() {
        var rule = new PlanExternalUDFs();
        System.out.println("Running PlanExternalUDFs test...");
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var input = builder.scan(table).build();
        builder.push(input);
        var x = builder.field(0);
        var fOp = builder.genericProjectionOp("f", RelType.fromString("f_type", true));
        var gOp = builder.genericProjectionOp("g", RelType.fromString("g_type", true));
        var f = builder.call(fOp, x);
        var g = builder.call(gOp, x);
        var before = builder.project(f, g).build();
        builder.push(input);
        var lower = builder.project(f, x).build();
        var rexBuilder = lower.getCluster().getRexBuilder();
        var fields = lower.getRowType().getFieldList();
        var first = rexBuilder.makeInputRef(fields.get(0).getType(), 0);
        var passed = rexBuilder.makeInputRef(fields.get(1).getType(), 1);
        var after = org.apache.calcite.rel.logical.LogicalProject.create(lower, java.util.List.of(),
                java.util.List.of(first, rexBuilder.makeCall(gOp, java.util.List.of(passed))),
                (java.util.List<String>) null);
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        runTest();
    }
}
