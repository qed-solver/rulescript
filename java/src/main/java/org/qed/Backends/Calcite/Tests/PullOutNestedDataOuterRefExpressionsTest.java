package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PullOutNestedDataOuterRefExpressions;
import org.qed.RuleBuilder;

public class PullOutNestedDataOuterRefExpressionsTest {
    public static void runTest() {
        var rule = new PullOutNestedDataOuterRefExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PullOutNestedDataOuterRefExpressions rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false, false);
        var inner = RuleChecks.table(builder, 1, false, false);
        SqlOperator gmv = builder.genericProjectionOp("Gmv", new org.qed.RelType.VarType("V", true));
        SqlOperator cmp = builder.genericPredicateOp("Cmp", true);
        var b = builder.transform(CalciteRuntime::configure);
        var left = b.scan(outer).build();
        var right = b.scan(inner).build();
        b.push(left).push(right).join(JoinRelType.INNER, b.literal(true));
        var nested = b.call(gmv, b.field(1, 0, 0));
        var condition = b.call(cmp, nested, b.field(1, 0, 1));
        var source = b.build();
        RelNode plan = LogicalFilter.create(source, condition);

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedSource = plain.scan(outer).scan(inner).join(JoinRelType.INNER, plain.literal(true));
        var projected = expectedSource.project(plain.field(0), plain.field(1),
                plain.call(gmv, plain.field(0)));
        var expected = projected.filter(plain.call(cmp, plain.field(2), plain.field(1)))
                .project(plain.field(0), plain.field(1)).build();
        RuleChecks.rewrites(rule, plan, expected);

        b.scan(outer).scan(inner).join(JoinRelType.LEFT, b.literal(true));
        var leftCondition = b.call(cmp, b.call(gmv, b.field(1, 0, 0)), b.field(1, 0, 1));
        var leftJoin = b.build();
        RuleChecks.doesNotApply(rule, LogicalFilter.create(leftJoin, leftCondition));
    }

    public static void main(String[] args) {
        System.out.println("Running PullOutNestedDataOuterRefExpressions test...");
        runTest();
    }
}
