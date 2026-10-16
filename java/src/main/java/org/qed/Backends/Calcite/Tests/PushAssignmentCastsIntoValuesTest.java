package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.PushAssignmentCastsIntoValues;
import org.qed.RuleBuilder;

public class PushAssignmentCastsIntoValuesTest {

    public static void runTest() {
        var rule = new PushAssignmentCastsIntoValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushAssignmentCastsIntoValues rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cast = (java.util.function.Supplier<RexNode>) () -> b.cast(b.field(0), SqlTypeName.BIGINT);
        var before = b.scan(t).project(cast.get(), b.literal(true)).build();
        var after = b.scan(t).project(cast.get()).project(b.field(0), b.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(cast.get(), b.literal(false)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PushAssignmentCastsIntoValues test...");
        runTest();
    }
}
