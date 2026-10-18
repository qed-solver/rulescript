package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldablePropagation;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public final class FoldablePropagationTest {
    public static void runTest() {
        var rule = new FoldablePropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsMissingProject(rule);
        rejectsNonFoldableProjection(rule);
    }

    private static void rewritesHandBuiltPlan(FoldablePropagation rule) {
        var plans = handBuiltPlans();
        RuleChecks.rewrites(rule, plans.before(), plans.after());
    }

    private static void rejectsNonFoldableProjection(FoldablePropagation rule) {
        var plans = handBuiltPlans();
        RuleChecks.doesNotApply(rule, plans.nonFoldable());
    }

    private static void rejectsMissingProject(FoldablePropagation rule) {
        var qedBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var table = RuleChecks.table(qedBuilder, 1, false);
        builder.scan(table);
        var rexBuilder = builder.getRexBuilder();
        var scan = builder.build();
        var predicateOperator = qedBuilder.genericPredicateOp("HandBuiltPredicate", true);
        RexNode x = rexBuilder.makeInputRef(scan, 0);
        var predicate = rexBuilder.makeCall(predicateOperator, x, x);
        RuleChecks.doesNotApply(rule, LogicalFilter.create(scan, predicate));
    }

    private static HandBuiltPlans handBuiltPlans() {
        var qedBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var table = RuleChecks.table(qedBuilder, 1, false);
        builder.scan(table);
        var rexBuilder = builder.getRexBuilder();
        var foldableOperator = qedBuilder.genericProjectionOp("HandBuiltConstant",
                new RelType.BaseType(SqlTypeName.INTEGER, true));
        var predicateOperator = qedBuilder.genericPredicateOp("HandBuiltPredicate", true);
        RexNode x = builder.field(0);
        RexNode foldable = rexBuilder.makeCall(foldableOperator, List.of());
        builder.project(List.of(x, foldable), List.of("x", "foldable"), true);
        RelNode project = builder.build();
        RexNode xRef = rexBuilder.makeInputRef(project, 0);
        RexNode foldableRef = rexBuilder.makeInputRef(project, 1);
        RexNode matchingPredicate = rexBuilder.makeCall(predicateOperator, foldableRef, xRef);
        RexNode rewrittenPredicate = rexBuilder.makeCall(predicateOperator, foldable, xRef);
        RexNode dependent = rexBuilder.makeCall(foldableOperator, List.of(x));
        var dependentProject = LogicalProject.create(project.getInput(0), List.of(), List.of(x, dependent),
                List.of("x", "foldable"));
        RexNode dependentPredicate = rexBuilder.makeCall(predicateOperator,
                rexBuilder.makeInputRef(dependentProject, 1), rexBuilder.makeInputRef(dependentProject, 0));
        return new HandBuiltPlans(LogicalFilter.create(project, matchingPredicate),
                LogicalFilter.create(project, rewrittenPredicate),
                LogicalFilter.create(dependentProject, dependentPredicate));
    }

    private record HandBuiltPlans(RelNode before, RelNode after, RelNode nonFoldable) {}

    public static void main(String[] args) {
        System.out.println("Running FoldablePropagation test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("FoldablePropagation test failed with " + CalciteTester.failures() + " failure(s)");
        }
    }
}
