package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RRuleInstances.IntersectToExists;
import org.qed.RuleBuilder;

public class IntersectToExistsTest {
    public static void runTest() {
        var rule = new IntersectToExists();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        handBuiltNearMisses(rule);
    }

    private static void handBuiltMatch(RRule rule) {
        var builder = RuleBuilder.create();
        var left = "A";
        var right = "B";
        builder.addTable(new org.qed.QedTable(left, kala.collection.Seq.of("col-A"), kala.collection.Seq.of(org.qed.RexRN.varType("T", true)), kala.collection.Set.empty(), kala.collection.Set.empty()));
        builder.addTable(new org.qed.QedTable(right, kala.collection.Seq.of("col-B"), kala.collection.Seq.of(org.qed.RexRN.varType("T", true)), kala.collection.Set.empty(), kala.collection.Set.empty()));
        RelNode input = builder.scan(left).scan(right).intersect(false, 2).build();
        var pattern = org.qed.RelRN.scan(left, "T");
        var other = org.qed.RelRN.scan(right, "T");
        var after = new org.qed.RelRN.Aggregate(pattern.correlate(JoinRelType.SEMI,
                SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, other), Seq.of(pattern.field(0)), Seq.empty());
        RuleChecks.rewrites(rule, input, FaithfulPlans.expected(after));
    }

    private static void handBuiltNearMisses(RRule rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        RelNode all = builder.scan(left).scan(right).intersect(true, 2).build();
        RuleChecks.doesNotApply(rule, all);

        builder = RuleBuilder.create();
        left = RuleChecks.table(builder, 2, false);
        right = RuleChecks.table(builder, 2, false);
        RelNode multiColumn = builder.scan(left).scan(right).intersect(false, 2).build();
        RuleChecks.doesNotApply(rule, multiColumn);
    }

    public static void main(String[] args) {
        System.out.println("Running IntersectToExists test...");
        runTest();
    }
}
