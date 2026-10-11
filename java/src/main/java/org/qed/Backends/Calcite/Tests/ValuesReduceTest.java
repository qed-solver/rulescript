package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ValuesReduce;
import org.qed.RuleBuilder;

import java.util.List;

public class ValuesReduceTest {

    public static void runTest() {
        var rule = new ValuesReduce();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ValuesReduce rule) {
        var base = RuleBuilder.create();
        var b = RuleChecks.ruleBuilder(base);
        var raw = b.transform(config -> config.withSimplifyValues(false));
        var projected = (java.util.function.Function<Object[], RelBuilder>) rows ->
                raw.values(new String[]{"a", "b", "c"}, rows);
        var before = projected.apply(new Object[]{1, 2, 3, 4, 5, 6}).project(raw.field(2), raw.field(0)).build();
        var after = b.values(new String[]{"c", "a"}, 3, 1, 6, 4).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, projected.apply(new Object[]{7, 8, 9, 4, 5, 6})
                .project(raw.field(2), raw.field(0)).build());
        RuleChecks.doesNotApply(rule, projected.apply(new Object[]{1, 2, 3, 4, 5, 6})
                .project(raw.call(SqlStdOperatorTable.PLUS, raw.field(2), raw.field(0)), raw.field(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ValuesReduce test...");
        runTest();
    }
}
