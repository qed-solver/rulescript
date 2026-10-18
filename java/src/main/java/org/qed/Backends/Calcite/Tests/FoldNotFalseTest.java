package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FoldNotFalse;
import org.qed.RuleBuilder;

public final class FoldNotFalseTest {
    public static void runTest() {
        var rule = new FoldNotFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var relBuilder = builder.transform(CalciteRuntime::configure);
        var table = RuleChecks.table(builder, 1, false);
        var input = relBuilder.scan(table).build();
        var predicate = relBuilder.call(SqlStdOperatorTable.NOT, relBuilder.literal(false));
        RelNode before = LogicalFilter.create(input, predicate);
        RelNode expected = input;
        RuleChecks.rewrites(rule, before, expected);

        var noMatchBuilder = RuleBuilder.create();
        var noMatchRelBuilder = noMatchBuilder.transform(CalciteRuntime::configure);
        var noMatchTable = RuleChecks.table(noMatchBuilder, 1, false);
        var noMatchInput = noMatchRelBuilder.scan(noMatchTable).build();
        var differentPredicate = noMatchRelBuilder.call(SqlStdOperatorTable.NOT,
                noMatchRelBuilder.call(SqlStdOperatorTable.IS_NULL,
                        noMatchRelBuilder.getRexBuilder().makeInputRef(noMatchInput, 0)));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(noMatchInput, differentPredicate));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotFalse test...");
        runTest();
    }
}
