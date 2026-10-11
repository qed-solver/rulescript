package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NegateComparison;
import org.qed.RuleBuilder;

public class NegateComparisonTest {

    public static void runTest() {
        var rule = new NegateComparison();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NegateComparison rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(l).scan(r);
        var before = b.join(JoinRelType.INNER, b.literal(true))
                .filter(b.call(SqlStdOperatorTable.NOT,
                        b.call(SqlStdOperatorTable.EQUALS, b.field(0), b.field(1))))
                .build();

        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.NOT_EQUALS, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var noNegation = RuleChecks.ruleBuilder(builder);
        noNegation.scan(l).scan(r);
        var plainEquality = noNegation.join(JoinRelType.INNER, noNegation.literal(true))
                .filter(noNegation.call(SqlStdOperatorTable.EQUALS, noNegation.field(0), noNegation.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, plainEquality);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateComparison test...");
        runTest();
    }
}
