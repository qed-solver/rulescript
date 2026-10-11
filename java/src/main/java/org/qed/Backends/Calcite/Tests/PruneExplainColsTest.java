package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneExplainCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneExplainColsTest {

    public static void runTest() {
        var rule = new PruneExplainCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneExplainCols rule) {
        var builder = RuleBuilder.create();
        var two = RuleChecks.table(builder, 2, false);
        var one = RuleChecks.table(builder, 1, false);
        var eOp = builder.genericProjectionOp("E", RelType.fromString("INTEGER", true));
        var before = builder.scan(two).project(builder.call(eOp, builder.field(0))).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(two).project(plain.field(0)).project(plain.call(eOp, plain.field(0))).build();
        RuleChecks.rewrites(rule, before, after);

        var narrow = builder.scan(one).project(builder.call(eOp, builder.field(0))).build();
        RuleChecks.doesNotApply(rule, narrow);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneExplainCols test...");
        runTest();
    }
}
