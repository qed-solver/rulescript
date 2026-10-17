package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CommuteNullIs;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.doesNotApply;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewrites;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesConcretePredicates;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesPattern;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rejectsNearMisses;

public final class CommuteNullIsTest {
    public static void runTest() {
        System.out.println("Running CommuteNullIs test...");
        var rule = new CommuteNullIs();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var qed = RuleBuilder.create();
        var name = RuleChecks.table(qed, 1, false);
        var builder = RuleChecks.ruleBuilder(qed);
        var input = builder.scan(name).build();
        var field = builder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0);
        var nullValue = builder.getRexBuilder().makeNullLiteral(field.getType());
        var before = builder.push(input).filter(builder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                nullValue, field)).build();
        var expected = builder.push(input).filter(builder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                field, nullValue)).build();
        rewrites(rule, before, expected);

        var nearMiss = builder.push(input).filter(builder.call(SqlStdOperatorTable.EQUALS,
                nullValue, field)).build();
        doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        runTest();
    }
}
