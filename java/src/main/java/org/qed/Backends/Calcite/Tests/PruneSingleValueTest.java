package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PruneSingleValue;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneSingleValueTest {

    public static void runTest() {
        var rule = new PruneSingleValue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneSingleValue rule) {
        var base = RuleBuilder.create();
        var o = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var withValues = (java.util.function.BiFunction<JoinRelType, Object[], RelBuilder>) (type, rows) -> {
            b.scan(o).values(new String[]{"v"}, rows);
            return b.join(type, RuleChecks.gt(b, b.field(2, 0, 0), 5));
        };
        var before = withValues.apply(JoinRelType.INNER, new Object[]{true}).build();
        var after = b.scan(o).filter(RuleChecks.gt(b, b.field(0), 5)).project(b.field(0), b.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, withValues.apply(JoinRelType.INNER, new Object[]{false}).build());
        RuleChecks.doesNotApply(rule, withValues.apply(JoinRelType.INNER, new Object[]{true, true}).build());
        RuleChecks.doesNotApply(rule, withValues.apply(JoinRelType.LEFT, new Object[]{true}).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneSingleValue test...");
        runTest();
    }
}
