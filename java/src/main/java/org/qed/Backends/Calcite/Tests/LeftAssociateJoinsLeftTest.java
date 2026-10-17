package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RRuleInstances.LeftAssociateJoinsLeft;
import org.qed.RuleBuilder;

public final class LeftAssociateJoinsLeftTest {
    public static void runTest() {
        var rule = new LeftAssociateJoinsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var before = tablePlan(false);
        var after = tablePlan(true);
        RuleChecks.rewrites(rule, before, after);

        var schema = RuleBuilder.create();
        var a = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var nearMiss = builder.scan(a).scan(b).join(JoinRelType.LEFT, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode tablePlan(boolean after) {
        var schema = RuleBuilder.create();
        var a = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.table(schema, 1, false);
        var c = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        SqlOperator bound = schema.genericPredicateOp("bound", true);
        SqlOperator unbound = schema.genericPredicateOp("unbound", true);
        if (!after) {
            var ab = builder.scan(a).scan(b).join(JoinRelType.INNER, builder.literal(true)).build();
            return builder.push(ab).scan(c).join(JoinRelType.INNER,
                    builder.and(builder.call(bound, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                            builder.call(unbound, builder.field(2, 0, 1), builder.field(2, 1, 0)))).build();
        }
        var ac = builder.scan(a).scan(c).join(JoinRelType.INNER,
                builder.call(bound, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var bac = builder.scan(b).push(ac).join(JoinRelType.INNER,
                builder.call(unbound, builder.field(2, 0, 0), builder.field(2, 1, 1))).build();
        return builder.push(bac).project(builder.field(1), builder.field(0), builder.field(2)).build();
    }

    public static void main(String[] args) {
        System.out.println("Running LeftAssociateJoinsLeft test...");
        runTest();
    }
}
