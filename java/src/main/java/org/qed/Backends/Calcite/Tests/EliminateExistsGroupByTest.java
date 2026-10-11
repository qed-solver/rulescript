package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateExistsGroupBy;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateExistsGroupByTest {

    public static void runTest() {
        var rule = new EliminateExistsGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateExistsGroupBy rule) {
        var base = RuleBuilder.create();
        var outer = RuleChecks.table(base, 1, false);
        var input = RuleChecks.table(base, 1, false);
        var before = base.scan(outer).scan(input)
                .project(List.of(base.field(0)), List.of(), true)
                .aggregate(base.groupKey(0))
                .join(JoinRelType.SEMI, base.literal(true)).build();
        var plain = RuleChecks.ruleBuilder(base);
        var after = plain.scan(outer).scan(input)
                .join(JoinRelType.SEMI, plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, base.scan(outer).scan(input)
                .project(List.of(base.field(0)), List.of(), true)
                .aggregate(base.groupKey(0))
                .join(JoinRelType.INNER, base.literal(true)).build());
        RuleChecks.doesNotApply(rule, base.scan(outer).scan(input)
                .project(List.of(base.field(0)), List.of(), true)
                .aggregate(base.groupKey(0), base.aggregateCall(SqlStdOperatorTable.SUM, base.field(0)))
                .join(JoinRelType.SEMI, base.literal(true)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateExistsGroupBy test...");
        runTest();
    }
}
