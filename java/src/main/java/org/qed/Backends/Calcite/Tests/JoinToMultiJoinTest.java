package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinToMultiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinToMultiJoinTest {

    public static void runTest() {
        var rule = new JoinToMultiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToMultiJoin rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 1, false);
        var y = RuleChecks.table(base, 1, false);
        var z = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var tree = (java.util.function.Function<JoinRelType, RelBuilder>) top -> {
            b.scan(x).scan(y).scan(z).join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
            return b.join(top, RuleChecks.gt(b, b.field(2, 0, 0), 5));
        };
        var before = tree.apply(JoinRelType.INNER).build();

        b.scan(x).scan(y).scan(z).join(JoinRelType.INNER, b.literal(true));
        var after = b.join(JoinRelType.INNER, b.and(RuleChecks.eq(b, b.field(2, 1, 0), b.field(2, 1, 1)),
                RuleChecks.gt(b, b.field(2, 0, 0), 5))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, tree.apply(JoinRelType.LEFT).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinToMultiJoin test...");
        runTest();
    }
}
