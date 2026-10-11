package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.MultiJoinOptimizeBushy;
import org.qed.RuleBuilder;

import java.util.List;

public class MultiJoinOptimizeBushyTest {

    public static void runTest() {
        var rule = new MultiJoinOptimizeBushy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MultiJoinOptimizeBushy rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var td = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var leftDeep = (java.util.function.Function<JoinRelType, RelBuilder>) top -> {
            b.scan(ta).scan(tb).join(JoinRelType.INNER, b.literal(true)).scan(tc)
                    .join(JoinRelType.INNER, b.literal(true)).scan(td);
            return b.join(top, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        };
        var before = leftDeep.apply(JoinRelType.INNER).build();

        b.scan(ta).scan(tb).join(JoinRelType.INNER, b.literal(true));
        b.scan(tc).scan(td).join(JoinRelType.INNER, b.literal(true));
        var after = b.join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, leftDeep.apply(JoinRelType.LEFT).build());
    }

    public static void main(String[] args) {
        System.out.println("Running MultiJoinOptimizeBushy test...");
        runTest();
    }
}
