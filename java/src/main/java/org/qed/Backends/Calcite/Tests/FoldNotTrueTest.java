package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldNotTrue;
import org.qed.RuleBuilder;

public class FoldNotTrueTest {
    public static void runTest() {
        var rule = new FoldNotTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(source).build();
        var condition = b.call(SqlStdOperatorTable.NOT, b.literal(true));
        var actual = org.apache.calcite.rel.logical.LogicalFilter.create(input, condition);
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(input, b.literal(false));
        RuleChecks.rewrites(rule, actual, expected);

        var noMatchBuilder = RuleBuilder.create();
        var noMatchSource = RuleChecks.table(noMatchBuilder, 1, false);
        var nb = RuleChecks.ruleBuilder(noMatchBuilder);
        var noMatchInput = nb.scan(noMatchSource).build();
        var noMatch = org.apache.calcite.rel.logical.LogicalFilter.create(noMatchInput,
                nb.call(SqlStdOperatorTable.NOT, nb.literal(false)));
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotTrue test...");
        runTest();
    }
}
