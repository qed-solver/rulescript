package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterMultiJoinMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterMultiJoinMergeTest {

    public static void runTest() {
        var rule = new FilterMultiJoinMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterMultiJoinMerge rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var threeWay = (java.util.function.Function<JoinRelType, RelBuilder>) top -> {
            b.scan(ta).scan(tb).join(JoinRelType.INNER, b.literal(true));
            return b.scan(tc).join(top, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        };
        var before = threeWay.apply(JoinRelType.INNER).filter(RuleChecks.gt(b, b.field(1), 5)).build();

        b.scan(ta).scan(tb).join(JoinRelType.INNER, b.literal(true)).scan(tc);
        var after = b.join(JoinRelType.INNER, b.and(RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                RuleChecks.gt(b, b.field(2, 0, 1), 5))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, threeWay.apply(JoinRelType.LEFT).filter(RuleChecks.gt(b, b.field(1), 5)).build());
        RuleChecks.doesNotApply(rule, threeWay.apply(JoinRelType.INNER).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterMultiJoinMerge test...");
        runTest();
    }
}
