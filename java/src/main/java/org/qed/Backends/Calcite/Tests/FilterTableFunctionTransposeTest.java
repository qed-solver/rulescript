package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterTableFunctionTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterTableFunctionTransposeTest {

    public static void runTest() {
        var rule = new FilterTableFunctionTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterTableFunctionTranspose rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 2, false);
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var function = (java.util.function.Function<JoinRelType, RelBuilder>) type -> b.scan(s).scan(t)
                .join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(List.of(b.field(0), b.field(1)), List.of(), true);
        var before = function.apply(JoinRelType.INNER).filter(RuleChecks.gt(b, b.field(0), 5)).build();
        var after = b.scan(s).filter(RuleChecks.gt(b, b.field(0), 5)).scan(t)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(List.of(b.field(0), b.field(1)), List.of(), true).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, function.apply(JoinRelType.LEFT).filter(RuleChecks.gt(b, b.field(0), 5)).build());
        RuleChecks.doesNotApply(rule, function.apply(JoinRelType.INNER).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterTableFunctionTranspose test...");
        runTest();
    }
}
