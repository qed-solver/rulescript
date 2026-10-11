package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.SemiJoinFilterTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class SemiJoinFilterTransposeTest {

    public static void runTest() {
        var rule = new SemiJoinFilterTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SemiJoinFilterTranspose rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 2, false);
        var y = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var semi = (java.util.function.Function<JoinRelType, RelBuilder>) type -> {
            b.scan(x).filter(RuleChecks.gt(b, b.field(0), 5)).scan(y);
            return b.join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        };
        var before = semi.apply(JoinRelType.SEMI).build();
        var after = b.scan(x).scan(y).join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .filter(RuleChecks.gt(b, b.field(0), 5)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, semi.apply(JoinRelType.INNER).build());
        RuleChecks.doesNotApply(rule, b.scan(x).scan(y)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinFilterTranspose test...");
        runTest();
    }
}
