package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.NegateOr;
import org.qed.RuleBuilder;

public class NegateOrTest {
    public static void runTest() {
        var rule = new NegateOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRule rule) {
        var catalog = RuleBuilder.create();
        var table = RuleChecks.table(catalog, 1, false);
        var b = RuleChecks.ruleBuilder(catalog);
        var input = b.scan(table).build();
        var x = b.getRexBuilder().makeInputRef(input, 0);
        var low = b.call(SqlStdOperatorTable.GREATER_THAN, x, b.literal(4));
        var high = b.call(SqlStdOperatorTable.LESS_THAN, x, b.literal(20));
        var before = b.push(input).filter(b.not(b.or(low, high))).build();
        var expected = b.push(input).filter(b.and(b.not(low), b.not(high))).build();
        RuleChecks.rewrites(rule, before, expected);

        var nearMiss = b.push(input).filter(b.not(b.or(low, high, b.call(
                SqlStdOperatorTable.EQUALS, x, b.literal(9))))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateOr test...");
        runTest();
    }
}
