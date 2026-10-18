package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateProjectConstantToDummyJoin;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public final class AggregateProjectConstantToDummyJoinTest {
    public static void runTest() {
        var rule = new AggregateProjectConstantToDummyJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var source = new RelRN.Scan(table, RexRN.varType("INTEGER", true), false);
        var project = new RelRN.ProjectMany(kala.collection.Seq.of(RexRN.trueLiteral(), RexRN.falseLiteral(), source.field(0)), source);
        var aggregate = new RelRN.Aggregate(project,
                kala.collection.Seq.of(project.field(0), project.field(1), project.field(2)),
                kala.collection.Seq.of(project.field(2).aggCall("f")));
        var dummy = RuleBuilder.create().values(new String[]{"x", "d"}, true, false).build();
        var joined = source.join(JoinRelType.INNER, RexRN.trueLiteral(), (RelRN) () -> dummy);
        var expectedProject = new RelRN.ProjectMany(kala.collection.Seq.of(joined.field(1), joined.field(2), joined.field(0)), joined);
        var expected = new RelRN.Aggregate(expectedProject,
                kala.collection.Seq.of(expectedProject.field(0), expectedProject.field(1), expectedProject.field(2)),
                kala.collection.Seq.of(expectedProject.field(2).aggCall("f")));
        RuleChecks.rewrites(rule, FaithfulPlans.of(aggregate), FaithfulPlans.expected(expected));

        var nearMiss = new RelRN.Aggregate(
                new RelRN.ProjectMany(kala.collection.Seq.of(RexRN.trueLiteral(), RexRN.falseLiteral(), source.field(0)), source),
                kala.collection.Seq.of(source.field(0)),
                kala.collection.Seq.of(source.field(0).aggCall("f")));
        RuleChecks.doesNotApply(rule, FaithfulPlans.of(nearMiss));
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateProjectConstantToDummyJoin test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}
