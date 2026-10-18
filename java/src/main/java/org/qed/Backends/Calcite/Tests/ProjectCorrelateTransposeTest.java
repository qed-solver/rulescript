package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.ProjectCorrelateTranspose;
import org.qed.RuleBuilder;

public final class ProjectCorrelateTransposeTest {
    public static void runTest() {
        var rule = new ProjectCorrelateTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectCorrelateTranspose rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false);
        var rightName = RuleChecks.table(builder, 2, false);
        var b = builder;
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var condition = b.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(1).getType(), 1),
                b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(1).getType(), 3));
        b.push(left).push(right).join(JoinRelType.INNER, condition)
                .project(java.util.List.of(b.field(0), b.field(2)));
        var input = b.build();
        RuleChecks.doesNotApply(rule, input);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
