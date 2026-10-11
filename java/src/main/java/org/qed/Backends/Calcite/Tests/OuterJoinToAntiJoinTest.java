package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.OuterJoinToAntiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class OuterJoinToAntiJoinTest {

    public static void runTest() {
        var rule = new OuterJoinToAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OuterJoinToAntiJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false, true);
        var r = RuleChecks.table(base, 1, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var unmatched = (java.util.function.BiFunction<JoinRelType, Boolean, RelBuilder>) (type, wantNull) -> {
            b.scan(l).scan(r).join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
            return b.filter(wantNull ? b.isNull(b.field(1)) : b.isNotNull(b.field(1)));
        };
        var before = unmatched.apply(JoinRelType.LEFT, true).project(b.field(0)).build();
        var after = b.scan(l).scan(r).join(JoinRelType.ANTI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, unmatched.apply(JoinRelType.LEFT, false).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, unmatched.apply(JoinRelType.INNER, true).project(b.field(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running OuterJoinToAntiJoin test...");
        runTest();
    }
}
