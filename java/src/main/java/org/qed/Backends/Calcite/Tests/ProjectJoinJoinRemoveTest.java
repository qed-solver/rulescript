package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectJoinJoinRemove;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectJoinJoinRemoveTest {

    public static void runTest() {
        var rule = new ProjectJoinJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectJoinJoinRemove rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, true, false);
        var other = RuleChecks.table(base, 1, true, false);
        var plainKey = RuleChecks.table(base, 1, false, false);
        var nullableKey = RuleChecks.table(base, 1, true, true);
        var c = RuleChecks.table(base, 1, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var chain = (java.util.function.BiFunction<String, String, RelBuilder>) (bottomLeft, bottomRight) -> {
            b.scan(bottomLeft).scan(bottomRight)
                    .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
            return b.scan(c).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        };
        var before = chain.apply(t, t).project(b.field(0), b.field(2)).build();
        var after = b.scan(t).scan(c).join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(0), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, chain.apply(plainKey, plainKey).project(b.field(0), b.field(2)).build());
        RuleChecks.doesNotApply(rule, chain.apply(nullableKey, nullableKey).project(b.field(0), b.field(2)).build());
        RuleChecks.doesNotApply(rule, chain.apply(t, other).project(b.field(0), b.field(2)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectJoinJoinRemove test...");
        runTest();
    }
}
