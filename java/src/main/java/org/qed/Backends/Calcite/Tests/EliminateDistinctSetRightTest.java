package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateDistinctSetRight;
import org.qed.RuleBuilder;

public class EliminateDistinctSetRightTest {
    public static void runTest() {
        var rule = new EliminateDistinctSetRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var rightName = RuleChecks.table(builder, 3, false);
        var emptyName = RuleChecks.table(builder, 3, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        b.scan(emptyName);
        var empty = b.build();
        b.push(empty).project(Seq.of(b.field(2), b.field(0), b.field(1)));
        empty = b.build();
        var emptyValues = LogicalValues.create(empty.getCluster(), empty.getRowType(), com.google.common.collect.ImmutableList.of());
        empty = emptyValues;
        b.scan(rightName).project(Seq.of(b.field(2), b.field(0), b.field(1)));
        var right = b.build();
        b.push(right).push(empty).union(false, 2);
        var before = b.build();
        var expectedBuilder = RuleBuilder.create();
        var expectedName = RuleChecks.table(expectedBuilder, 3, false);
        RelBuilder e = RuleChecks.ruleBuilder(expectedBuilder);
        e.scan(expectedName).project(Seq.of(e.field(2), e.field(0), e.field(1)))
                .aggregate(e.groupKey(e.field(0), e.field(1), e.field(2)));
        RuleChecks.rewrites(rule, before, e.build());

        var allUnion = LogicalUnion.create(((LogicalUnion) before).getInputs(), true);
        RuleChecks.doesNotApply(rule, allUnion);
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("EliminateDistinctSetRight test failed");
        }
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateDistinctSetRight test...");
        runTest();
    }
}
