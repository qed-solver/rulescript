package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectJoinRemove;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectJoinRemoveTest {

    public static void runTest() {
        var rule = new ProjectJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectJoinRemove rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, true, false);
        var other = RuleChecks.table(base, 1, true, false);
        var plainKey = RuleChecks.table(base, 1, false, false);
        var nullableKey = RuleChecks.table(base, 1, true, true);
        var b = RuleChecks.ruleBuilder(base);
        var selfJoin = (java.util.function.BiFunction<String, JoinRelType, RelBuilder>) (table, type) -> b.scan(table)
                .scan(table).join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = selfJoin.apply(t, JoinRelType.LEFT).project(b.field(0)).build();
        var after = b.scan(t).project(b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, selfJoin.apply(plainKey, JoinRelType.LEFT).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, selfJoin.apply(nullableKey, JoinRelType.LEFT).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, selfJoin.apply(t, JoinRelType.INNER).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, b.scan(t).scan(other)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).project(b.field(0))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectJoinRemove test...");
        runTest();
    }
}
