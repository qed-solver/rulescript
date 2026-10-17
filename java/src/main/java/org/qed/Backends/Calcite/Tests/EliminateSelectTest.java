package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateSelect;
import org.qed.RuleBuilder;

public class EliminateSelectTest {
    public static void runTest() {
        var rule = new EliminateSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var source = b.scan(table).build();
        var positive = org.apache.calcite.rel.logical.LogicalFilter.create(source,
                b.getRexBuilder().makeLiteral(true));
        RuleChecks.rewrites(rule, positive, source);

        var noMatchBuilder = RuleBuilder.create();
        var noMatchTable = RuleChecks.table(noMatchBuilder, 1, false);
        var nb = RuleChecks.ruleBuilder(noMatchBuilder);
        var noMatchSource = nb.scan(noMatchTable).build();
        var field = nb.getRexBuilder().makeInputRef(noMatchSource.getRowType().getFieldList().get(0).getType(), 0);
        var noMatch = org.apache.calcite.rel.logical.LogicalFilter.create(noMatchSource,
                nb.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN, field, nb.literal(7)));
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSelect test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " test(s) failed");
        }
    }
}
