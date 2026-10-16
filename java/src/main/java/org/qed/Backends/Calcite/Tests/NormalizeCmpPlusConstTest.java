package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NormalizeCmpPlusConst;
import org.qed.RuleBuilder;

public class NormalizeCmpPlusConstTest {

    public static void runTest() {
        var rule = new NormalizeCmpPlusConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeCmpPlusConst rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).filter(b.call(SqlStdOperatorTable.LESS_THAN,
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(2))).build();
        var after = b.scan(t).filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0),
                b.call(SqlStdOperatorTable.MINUS, b.field(2), b.field(1)))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).filter(b.call(SqlStdOperatorTable.GREATER_THAN,
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(2))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).filter(b.call(SqlStdOperatorTable.LESS_THAN,
                b.call(SqlStdOperatorTable.MINUS, b.field(0), b.field(1)), b.field(2))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeCmpPlusConst test...");
        runTest();
    }
}
