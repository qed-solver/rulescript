package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ProjectInnerJoinValues;
import org.qed.RuleBuilder;

public class ProjectInnerJoinValuesTest {

    public static void runTest() {
        var rule = new ProjectInnerJoinValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        singleRowValues(rule);
    }

    private static void singleRowValues(ProjectInnerJoinValues rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l)
                .values(new String[]{"v0", "v1"}, 1, 2)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.GREATER_THAN,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l)
                .project(plain.field(0), plain.literal(1), plain.literal(2))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftOuter = builder.scan(l)
                .values(new String[]{"v0", "v1"}, 1, 2)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.GREATER_THAN,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, leftOuter);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectInnerJoinValues test...");
        runTest();
    }
}
