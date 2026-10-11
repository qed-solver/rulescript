package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectToSemiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectToSemiJoinTest {

    public static void runTest() {
        var rule = new ProjectToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectToSemiJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var joined = (java.util.function.BiFunction<JoinRelType, Integer, RelBuilder>) (type, key) ->
                b.scan(l).scan(r).aggregate(b.groupKey(key))
                        .join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = joined.apply(JoinRelType.INNER, 1).project(b.field(1), b.field(0)).build();
        var after = b.scan(l).scan(r).join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)))
                .project(b.field(1), b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, joined.apply(JoinRelType.LEFT, 1).project(b.field(1), b.field(0)).build());
        RuleChecks.doesNotApply(rule, joined.apply(JoinRelType.INNER, 0).project(b.field(1), b.field(0)).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .aggregate(b.groupKey(1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0)))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(1), b.field(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectToSemiJoin test...");
        runTest();
    }
}
