package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CombineTypedFilters;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class CombineTypedFiltersTest {
    public static void runTest() {
        var rule = new CombineTypedFilters();
        rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        rewritesConcretePredicates();
        rejectsLeftFilter();
    }

    private static void rewritesConcretePredicates() {
        var builder = RuleBuilder.create();
        var name = table(builder, 1, false);
        var b = ruleBuilder(builder);
        RelNode before = b.scan(name).filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(20)))
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10))).build();
        RelNode after = b.scan(name).filter(b.call(SqlStdOperatorTable.AND,
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10)),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(20)))).build();
        rewrites(new CombineTypedFilters(), before, after);
    }

    private static void rejectsLeftFilter() {
        var builder = RuleBuilder.create();
        var name = table(builder, 1, false);
        var b = ruleBuilder(builder);
        var input = b.scan(name).build();
        var rex = b.getRexBuilder();
        var predicate = rex.makeCall(SqlStdOperatorTable.LESS_THAN, rex.makeInputRef(input, 0),
                rex.makeExactLiteral(java.math.BigDecimal.valueOf(20)));
        var filter = org.apache.calcite.rel.logical.LogicalFilter.create(input, predicate);
        var left = org.apache.calcite.rel.logical.LogicalJoin.create(filter, input, java.util.List.of(),
                rex.makeLiteral(true), java.util.Set.of(), JoinRelType.LEFT);
        doesNotApply(new CombineTypedFilters(), left);
    }

    public static void main(String[] args) {
        System.out.println("Running CombineTypedFilters test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("CombineTypedFilters test failed");
        }
    }
}
