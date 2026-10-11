package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.JoinCommute;
import org.qed.RuleBuilder;

public class JoinCommuteTest {

    public static void runTest() {
        var rule = new JoinCommute();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinCommute rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var lessThan = builder.call(SqlStdOperatorTable.LESS_THAN, builder.scan(l).scan(r).field(2, 0, 0),
                builder.field(2, 1, 0));
        var plan = builder.join(JoinRelType.INNER, lessThan).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var swapped = plain.scan(r).scan(l).join(JoinRelType.INNER,
                plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(2, 1, 0), plain.field(2, 0, 0)))
                .project(plain.field(1), plain.field(0)).build();
        RuleChecks.rewrites(rule, plan, swapped);
        var left = builder.scan(l).scan(r).join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.LESS_THAN,
                builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, left);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinCommute test...");
        runTest();
    }
}
