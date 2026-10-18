package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.BooleanSimplification;
import org.qed.RuleBuilder;

public final class BooleanSimplificationTest {
    public static void runTest() {
        var rule = new BooleanSimplification();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var scan = relBuilder.scan(table).build();
        var rexBuilder = relBuilder.getRexBuilder();
        var value = rexBuilder.makeInputRef(scan, 0);
        var a = lessThan(rexBuilder, value, 10);
        var b = lessThan(rexBuilder, value, 20);
        var c = lessThan(rexBuilder, value, 30);

        var before = filter(scan, rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeCall(SqlStdOperatorTable.OR, a, b),
                rexBuilder.makeCall(SqlStdOperatorTable.OR, a, c)));
        var expected = filter(scan, rexBuilder.makeCall(SqlStdOperatorTable.OR, a,
                rexBuilder.makeCall(SqlStdOperatorTable.AND, b, c)));
        RuleChecks.rewrites(rule, before, expected);

        var nearMiss = filter(scan, rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeCall(SqlStdOperatorTable.OR, a, b), a));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RexNode lessThan(org.apache.calcite.rex.RexBuilder rexBuilder, RexNode value, int literal) {
        return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN, value, rexBuilder.makeExactLiteral(
                java.math.BigDecimal.valueOf(literal)));
    }

    private static RelNode filter(RelNode input, RexNode condition) {
        return LogicalFilter.create(input, condition);
    }

    public static void main(String[] args) {
        System.out.println("Running BooleanSimplification test...");
        runTest();
    }
}
