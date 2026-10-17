package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RRuleInstances.ProjectJoinJoinRemove;
import org.qed.RuleBuilder;

public final class ProjectJoinJoinRemoveTest {
    public static void runTest() {
        var rule = new ProjectJoinJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        RuleChecks.rewrites(rule, tablePlan(false), tablePlan(true));
        var schema = RuleBuilder.create();
        var t = RuleChecks.table(schema, 1, false);
        var c = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var nearMiss = builder.scan(t).scan(t).join(JoinRelType.LEFT,
                RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(c).join(JoinRelType.LEFT,
                        RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.field(0), builder.field(2)).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode tablePlan(boolean after) {
        var schema = RuleBuilder.create();
        var t = RuleChecks.table(schema, 1, true, false);
        var c = RuleChecks.table(schema, 1, false, false);
        var builder = RuleChecks.ruleBuilder(schema);
        if (!after) {
            var tt = builder.scan(t).scan(t).join(JoinRelType.LEFT,
                    RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
            return builder.push(tt).scan(c).join(JoinRelType.LEFT,
                    RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                    .project(builder.field(0), builder.field(2)).build();
        }
        return builder.scan(t).scan(c).join(JoinRelType.LEFT,
                RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.field(0), builder.field(1)).build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
