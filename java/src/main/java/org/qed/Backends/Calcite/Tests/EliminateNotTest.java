package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateNot;
import org.qed.RuleBuilder;

public class EliminateNotTest {

    public static void runTest() {
        var rule = new EliminateNot();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        doubleNegation(rule);
    }

    private static void doubleNegation(EliminateNot rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        plain.scan(table);
        var inner = RuleChecks.gt(plain, plain.field(0), 5);
        var before = plain.filter(plain.call(SqlStdOperatorTable.NOT,
                plain.call(SqlStdOperatorTable.NOT, inner))).build();
        plain.scan(table);
        var after = plain.filter(inner).build();
        RuleChecks.rewrites(rule, before, after);

        plain.scan(table);
        var single = plain.filter(plain.call(SqlStdOperatorTable.NOT, inner)).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateNot test...");
        runTest();
    }
}
