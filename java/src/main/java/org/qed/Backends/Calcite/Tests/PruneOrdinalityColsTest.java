package org.qed.Backends.Calcite.Tests;

import java.util.List;

import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRuleInstances.PruneOrdinalityCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneOrdinalityColsTest {

    public static void runTest() {
        var rule = new PruneOrdinalityCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneOrdinalityCols rule) {
        var builder = RuleBuilder.create();
        var three = RuleChecks.table(builder, 3, false);
        var oOp = builder.genericProjectionOp("O", RelType.fromString("INTEGER", true));
        var gOp = builder.genericProjectionOp("G", RelType.fromString("INTEGER", true));
        var plain = RuleChecks.ruleBuilder(builder);
        var rx = plain.getRexBuilder();
        var scanned = plain.scan(three).build();
        var ord = LogicalProject.create(scanned, List.of(), List.of(
                rx.makeInputRef(scanned, 0), rx.makeInputRef(scanned, 1), rx.makeInputRef(scanned, 2),
                rx.makeCall(oOp, rx.makeInputRef(scanned, 0), rx.makeInputRef(scanned, 1))), (List<String>) null);
        var before = LogicalProject.create(ord, List.of(), List.of(
                rx.makeCall(gOp, rx.makeInputRef(ord, 0), rx.makeInputRef(ord, 1), rx.makeInputRef(ord, 3))),
                (List<String>) null);
        var pruned = plain.scan(three).build();
        var prunedOrd = LogicalProject.create(LogicalProject.create(pruned, List.of(), List.of(
                rx.makeInputRef(pruned, 0), rx.makeInputRef(pruned, 1)), (List<String>) null), List.of(), List.of(
                rx.makeInputRef(pruned, 0), rx.makeInputRef(pruned, 1),
                rx.makeCall(oOp, rx.makeInputRef(pruned, 0), rx.makeInputRef(pruned, 1))), (List<String>) null);
        var after = LogicalProject.create(prunedOrd, List.of(), List.of(
                rx.makeCall(gOp, rx.makeInputRef(prunedOrd, 0), rx.makeInputRef(prunedOrd, 1),
                        rx.makeInputRef(prunedOrd, 2))), (List<String>) null);
        RuleChecks.rewrites(rule, before, after);

        var nearMissScan = plain.scan(three).build();
        var nearMissOrd = LogicalProject.create(nearMissScan, List.of(), List.of(
                rx.makeInputRef(nearMissScan, 0), rx.makeInputRef(nearMissScan, 1), rx.makeInputRef(nearMissScan, 2),
                rx.makeCall(oOp, rx.makeInputRef(nearMissScan, 0), rx.makeInputRef(nearMissScan, 2))),
                (List<String>) null);
        var nearMiss = LogicalProject.create(nearMissOrd, List.of(), List.of(
                rx.makeCall(gOp, rx.makeInputRef(nearMissOrd, 0), rx.makeInputRef(nearMissOrd, 1),
                        rx.makeInputRef(nearMissOrd, 3))), (List<String>) null);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneOrdinalityCols test...");
        runTest();
    }
}
