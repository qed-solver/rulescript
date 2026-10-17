package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ProjectSetOpTranspose;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectSetOpTransposeTest {
    public static void runTest() {
        var rule = new ProjectSetOpTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectSetOpTranspose rule) {
        var schema = RuleBuilder.create();
        var leftName = RuleChecks.table(schema, 1, false);
        var rightName = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var op = ((RexRN.Proj) ((RelRN.Project) rule.before()).map()).operator();
        builder.scan(leftName).scan(rightName).union(true, 2);
        builder.project(List.of(builder.call(op, builder.field(0))));
        RelNode before = builder.build();

        var expectedBuilder = RuleChecks.ruleBuilder(schema);
        expectedBuilder.scan(leftName);
        var left = expectedBuilder.build();
        var leftBuilder = RuleChecks.ruleBuilder(schema);
        leftBuilder.push(left).project(List.of(leftBuilder.call(op, leftBuilder.field(0))));
        left = leftBuilder.build();
        var rightBuilder = RuleChecks.ruleBuilder(schema);
        rightBuilder.scan(rightName).project(List.of(rightBuilder.call(op, rightBuilder.field(0))));
        var right = rightBuilder.build();
        expectedBuilder.push(left).push(right).union(true, 2);
        RelNode after = expectedBuilder.build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectSetOpTranspose test...");
        runTest();
    }
}
