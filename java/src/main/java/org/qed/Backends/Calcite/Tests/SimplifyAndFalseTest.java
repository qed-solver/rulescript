package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyAndFalse;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class SimplifyAndFalseTest {
    public static void runTest() {
        var rule = new SimplifyAndFalse();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var ruleBuilder = RuleBuilder.create();
        var table = table(ruleBuilder, 1, false, true);
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        RexNode amount = rex.makeInputRef(input, 0);
        RexNode predicate = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, amount, rex.makeExactLiteral(
                java.math.BigDecimal.TEN));
        RexNode conjunction = rex.makeCall(SqlStdOperatorTable.AND, predicate, rex.makeLiteral(false));
        RelNode before = LogicalFilter.create(input, conjunction);
        RelNode expected = LogicalFilter.create(input, rex.makeLiteral(false));
        rewrites(rule, before, expected);

        RelNode nearMiss = LogicalFilter.create(input, predicate);
        doesNotApply(rule, nearMiss);
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("SimplifyAndFalse failed " + CalciteTester.failures() + " check(s)");
        }
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyAndFalse test...");
        runTest();
    }
}
