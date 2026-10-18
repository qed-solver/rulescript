package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.MergeProjectWithValues;
import org.qed.RuleBuilder;

import java.util.List;

public class MergeProjectWithValuesTest {
    public static void runTest() {
        var rule = new MergeProjectWithValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RelBuilder builder() {
        return RuleBuilder.create().transform(CalciteRuntime::configure);
    }

    private static void ordinaryTables(MergeProjectWithValues rule) {
        var beforeBuilder = builder();
        var beforeValues = beforeBuilder.values(new String[]{"c0", "c1"}, 1, 2).build();
        var before = LogicalProject.create(beforeValues, List.of(),
                List.of(beforeBuilder.getRexBuilder().makeInputRef(beforeValues, 0),
                        beforeBuilder.literal(3)), List.of("c0", "c1"));

        var expectedBuilder = builder();
        var expected = expectedBuilder.values(new String[]{"c0", "c1"}, 1, 3).build();
        RuleChecks.rewrites(rule, before, expected);

        var nearBuilder = builder();
        var nearValues = nearBuilder.values(new String[]{"c0", "c1"}, 1, 2).build();
        RelNode near = LogicalProject.create(nearValues, List.of(),
                List.of(nearBuilder.getRexBuilder().makeInputRef(nearValues, 0),
                        nearBuilder.literal(4)), List.of("c0", "c1"));
        RuleChecks.doesNotApply(rule, near);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("MergeProjectWithValues test failures: " + CalciteTester.failures());
        }
    }
}
