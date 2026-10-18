package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalValues;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CalcReduceExpressions;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class CalcReduceExpressionsTest {
    public static void runTest() {
        var rule = new CalcReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var scan = rel.scan(table).build();
        var rex = scan.getCluster().getRexBuilder();
        var filter = LogicalFilter.create(scan, rex.makeLiteral(false));
        var e1 = builder.genericProjectionOp("E1", RelType.fromString("INTEGER", true));
        var e2 = builder.genericProjectionOp("E2", RelType.fromString("INTEGER", true));
        var x = rex.makeInputRef(scan, 0);
        var y = rex.makeInputRef(scan, 1);
        var before = LogicalProject.create(filter, List.of(),
                List.of(rex.makeCall(e1, x, y), rex.makeCall(e2, x)), List.of("e1", "e2"));
        var expected = emptyWithOutputType(before);
        RuleChecks.rewrites(rule, before, expected);

        var nearBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearBuilder, 2, false);
        var nearRel = RuleChecks.ruleBuilder(nearBuilder);
        var nearScan = nearRel.scan(nearTable).build();
        var nearRex = nearScan.getCluster().getRexBuilder();
        var nearFilter = LogicalFilter.create(nearScan, nearRex.makeLiteral(true));
        var nearE1 = nearBuilder.genericProjectionOp("E1", RelType.fromString("INTEGER", true));
        var nearE2 = nearBuilder.genericProjectionOp("E2", RelType.fromString("INTEGER", true));
        var nearX = nearRex.makeInputRef(nearScan, 0);
        var nearY = nearRex.makeInputRef(nearScan, 1);
        var nearPlan = LogicalProject.create(nearFilter, List.of(),
                List.of(nearRex.makeCall(nearE1, nearX, nearY), nearRex.makeCall(nearE2, nearX)), List.of("e1", "e2"));
        RuleChecks.doesNotApply(rule, nearPlan);
    }

    private static RelNode emptyWithOutputType(RelNode projected) {
        return LogicalValues.createEmpty(projected.getCluster(), projected.getRowType());
    }

    public static void main(String[] args) {
        System.out.println("Running CalcReduceExpressions test...");
        runTest();
    }
}
