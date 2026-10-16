package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CommuteNullIs;
import org.qed.RuleBuilder;

public class CommuteNullIsTest {

    public static void runTest() {
        var rule = new CommuteNullIs();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CommuteNullIs rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(t);
        var nul = b.getRexBuilder().makeNullLiteral(b.field(0).getType());
        var before = b.filter(b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, nul, b.field(0))).build();
        b.scan(t);
        var after = b.filter(b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b.field(0), nul)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, after);
        RuleChecks.doesNotApply(rule, b.scan(t)
                .filter(b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b.literal(1), b.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteNullIs test...");
        runTest();
    }
}
