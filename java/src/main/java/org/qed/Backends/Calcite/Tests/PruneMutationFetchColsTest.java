package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RRuleInstances.PruneMutationFetchCols;
import org.qed.RuleBuilder;

public class PruneMutationFetchColsTest {
    public static void runTest() {
        var rule = new PruneMutationFetchCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneMutationFetchCols rule) {
        var source = RelRN.scanMany("S", Seq.of(
                new RelType.VarType("I0_Type", true),
                new RelType.VarType("I1_Type", true)));
        var mutation = RuleBuilder.create().genericProjectionOp("M", new RelType.VarType("M_Type", true));
        var before = FaithfulPlans.of(source.project(new RexRN.Proj(mutation, Seq.of(source.field(0)))));
        var pruned = source.project(source.field(0));
        var after = FaithfulPlans.expected(pruned.project(
                new RexRN.Proj(mutation, Seq.of(pruned.field(0)))));
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneMutationFetchCols test...");
        runTest();
    }
}
