package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.PruneFileSourcePartitions;

public final class PruneFileSourcePartitionsTest {
    public static void runTest() {
        var rule = new PruneFileSourcePartitions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var scan = RelRN.scanMany(table, Seq.of(new RelType.VarType("T0", true),
                new RelType.VarType("T1", true), new RelType.VarType("T2", true)));
        var partitionOp = builder.genericPredicateOp("P", true);
        var dataOp = builder.genericPredicateOp("D", true);
        var projectionOp = builder.genericProjectionOp("Pi", new RelType.VarType("Pi_Type", true));
        var partition = new RexRN.Pred(partitionOp, Seq.of(scan.field(0), scan.field(1)));
        var data = scan.pred("D");
        var full = RexRN.and(partition, data);
        var project = new RexRN.Proj(projectionOp, Seq.of(scan.field(0), scan.field(1), scan.field(2)));
        var before = scan.filter(full).project(project);
        var pruned = scan.filter(partition);
        var after = pruned.filter(full).project(project);
        RuleChecks.rewrites(rule, FaithfulPlans.of(before), FaithfulPlans.expected(after));
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
