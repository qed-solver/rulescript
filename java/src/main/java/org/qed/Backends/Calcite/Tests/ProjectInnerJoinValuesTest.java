package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ProjectInnerJoinValues;
import org.qed.RuleBuilder;

public class ProjectInnerJoinValuesTest {
    public static void runTest() {
        var rule = new ProjectInnerJoinValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectInnerJoinValues rule) {
        var qed = RuleBuilder.create();
        var leftName = RuleChecks.table(qed, 1, false, false);
        var b = qed.transform(CalciteRuntime::configure);
        RelNode left = b.scan(leftName).build();
        RelNode right = b.values(new String[]{"v0", "v1"}, 1, 2).build();
        RelNode before = LogicalJoinFactory.join(b, left, right);

        var expectedQED = RuleBuilder.create();
        RuleChecks.table(expectedQED, 1, false, false);
        var expectedBuilder = expectedQED.transform(CalciteRuntime::configure);
        RelNode expectedInput = expectedBuilder.scan(leftName).build();
        RelNode expected = expectedBuilder.push(expectedInput)
                .project(java.util.List.of(expectedBuilder.field(0), expectedBuilder.literal(1), expectedBuilder.literal(2)))
                .build();
        RuleChecks.rewrites(rule, before, expected);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }

    private static final class LogicalJoinFactory {
        static RelNode join(RelBuilder b, RelNode left, RelNode right) {
            b.push(left);
            b.push(right);
            var condition = b.literal(true);
            return org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                    condition, java.util.Set.of(), JoinRelType.INNER);
        }
    }
}
