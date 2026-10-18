package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.rel.logical.LogicalMinus;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ReplaceExceptWithFilter;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class ReplaceExceptWithFilterTest {
    public static void runTest() {
        var rule = new ReplaceExceptWithFilter();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var table = table(builder, 1, false, true);
        var relBuilder = ruleBuilder(builder);
        var input = relBuilder.scan(table).build();
        var value = relBuilder.getRexBuilder().makeInputRef(input, 0);
        RexNode leftPredicate = gt(relBuilder, value, 10);
        RexNode rightPredicate = eq(relBuilder, value, relBuilder.literal(20));
        RelNode left = LogicalFilter.create(input, leftPredicate);
        RelNode right = LogicalFilter.create(input, rightPredicate);
        RelNode before = LogicalMinus.create(List.of(left, right), false);

        RexNode nullSafeNotRight = relBuilder.call(SqlStdOperatorTable.OR,
                relBuilder.call(SqlStdOperatorTable.NOT, rightPredicate),
                relBuilder.call(SqlStdOperatorTable.IS_NULL, rightPredicate));
        RelNode filtered = LogicalFilter.create(left, nullSafeNotRight);
        RelNode expected = LogicalIntersect.create(List.of(filtered, filtered), false);
        rewrites(rule, before, expected);

        doesNotApply(rule, LogicalMinus.create(List.of(left, right), true));
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceExceptWithFilter test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("ReplaceExceptWithFilter test failures: " + CalciteTester.failures());
        }
    }
}
