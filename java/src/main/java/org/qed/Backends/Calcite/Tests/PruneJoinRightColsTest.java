package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneJoinRightCols;

public class PruneJoinRightColsTest {
    public static void runTest() {
        var rule = new PruneJoinRightCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneJoinRightCols rule) {
        var builder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 2, false);
        var b = RuleChecks.ruleBuilder(builder);
        var l = b.scan(leftName).build();
        var r = b.scan(rightName).build();
        var leftRef = b.getRexBuilder().makeInputRef(l.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = b.getRexBuilder().makeInputRef(r.getRowType().getFieldList().get(0).getType(), 1);
        var before = b.push(l).push(r)
                .join(JoinRelType.INNER, RuleChecks.eq(b, leftRef, rightRef))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build();

        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 2, false);
        var e = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedL = e.scan(expectedLeft).build();
        var expectedR = e.scan(expectedRight).project(e.field(0)).build();
        var expectedLeftRef = e.getRexBuilder().makeInputRef(expectedL.getRowType().getFieldList().get(0).getType(), 0);
        var expectedRightRef = e.getRexBuilder().makeInputRef(expectedR.getRowType().getFieldList().get(0).getType(), 1);
        var after = e.push(expectedL).push(expectedR)
                .join(JoinRelType.INNER, RuleChecks.eq(e, expectedLeftRef, expectedRightRef))
                .project(e.call(SqlStdOperatorTable.PLUS, e.field(0), e.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneJoinRightCols test...");
        runTest();
    }
}
