package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.LeftAssociateJoinsRight;
import org.qed.RuleBuilder;

public class LeftAssociateJoinsRightTest {
    public static void runTest() {
        var rule = new LeftAssociateJoinsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(LeftAssociateJoinsRight rule) {
        var builder = RuleBuilder.create();
        var aName = RuleChecks.table(builder, 1, false);
        var bName = RuleChecks.table(builder, 1, false);
        var cName = RuleChecks.table(builder, 1, false);
        var before = builder.scan(aName).scan(bName).join(JoinRelType.INNER, builder.literal(true))
                .scan(cName).join(JoinRelType.INNER, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, before);
        System.out.println("succeeded (ordinary table join without required predicates rejected)");
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
