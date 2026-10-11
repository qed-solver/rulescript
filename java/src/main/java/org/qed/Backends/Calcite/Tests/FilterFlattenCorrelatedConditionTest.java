package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterFlattenCorrelatedCondition;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterFlattenCorrelatedConditionTest {

    public static void runTest() {
        var rule = new FilterFlattenCorrelatedCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterFlattenCorrelatedCondition rule) {
        var base = RuleBuilder.create();
        var outer = RuleChecks.table(base, 1, false);
        var inner = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var product = (java.util.function.Function<JoinRelType, RelBuilder>) type ->
                b.scan(outer).scan(inner).join(type, b.literal(true));
        var before = product.apply(JoinRelType.INNER)
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0),
                        b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1)))).build();
        var after = product.apply(JoinRelType.INNER)
                .project(b.field(0), b.field(1), b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1)))
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.field(2)))
                .project(b.field(0), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, product.apply(JoinRelType.LEFT)
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0),
                        b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1)))).build());
        RuleChecks.doesNotApply(rule, product.apply(JoinRelType.INNER)
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(1),
                        b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1)))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterFlattenCorrelatedCondition test...");
        runTest();
    }
}
