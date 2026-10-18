package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.LoptOptimizeJoin;
import org.qed.RuleBuilder;

public class LoptOptimizeJoinTest {
    public static void runTest() {
        var rule = new LoptOptimizeJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(LoptOptimizeJoin rule) {
        var catalog = RuleBuilder.create();
        var aName = RuleChecks.table(catalog, 1, false);
        var bName = RuleChecks.table(catalog, 1, false);
        var cName = RuleChecks.table(catalog, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(catalog);
        var a = b.scan(aName).build();
        var bb = b.scan(bName).build();
        var c = b.scan(cName).build();
        var before = b.push(a).push(bb).join(JoinRelType.INNER, b.literal(true))
                .push(c).join(JoinRelType.INNER, b.literal(true)).build();
        RuleChecks.doesNotApply(rule, before);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("Calcite test failures: " + CalciteTester.failures());
        }
    }
}
