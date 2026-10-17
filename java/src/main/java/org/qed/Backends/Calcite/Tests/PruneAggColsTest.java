package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.RRule;
import org.qed.RRuleInstances.PruneAggCols;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneAggColsTest {
    public static void runTest() {
        var rule = new PruneAggCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var scan = RelRN.scanMany(table, kala.collection.Seq.of(
                new RelType.VarType("K_Type", true),
                new RelType.VarType("A_Type", true),
                new RelType.VarType("B_Type", true)));
        var key = scan.field(0);
        var f = new RelRN.AggCall("f", false, new RelType.VarType("F_Type", true), kala.collection.Seq.of(scan.field(1)));
        var g = new RelRN.AggCall("g", false, new RelType.VarType("G_Type", true), kala.collection.Seq.of(scan.field(2)));
        var aggregate = new RelRN.Aggregate(scan, kala.collection.Seq.of(key), kala.collection.Seq.of(f, g));
        var before = FaithfulPlans.of(aggregate.project(kala.collection.Seq.of(aggregate.field(0), aggregate.field(1))));
        var after = FaithfulPlans.of(new RelRN.Aggregate(scan, kala.collection.Seq.of(key), kala.collection.Seq.of(f)) );
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = FaithfulPlans.of(aggregate.project(kala.collection.Seq.of(aggregate.field(0), aggregate.field(2))));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneAggCols test...");
        runTest();
    }
}
