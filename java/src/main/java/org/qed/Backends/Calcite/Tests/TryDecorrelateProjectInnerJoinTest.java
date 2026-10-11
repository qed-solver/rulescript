package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryDecorrelateProjectInnerJoin;
import org.qed.RuleBuilder;

public class TryDecorrelateProjectInnerJoinTest {

    public static void runTest() {
        var rule = new TryDecorrelateProjectInnerJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateProjectInnerJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var il = RuleChecks.table(base, 1, false);
        var ir = RuleChecks.table(base, 1, false);
        var builder = base;
        var before = builder.scan(l)
                .scan(il).scan(ir)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.field(0), builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(1)))
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 1), builder.field(2, 0, 0))))
                .build();
        var plain = RuleChecks.ruleBuilder(base);
        var after = plain.scan(l)
                .scan(il).scan(ir)
                .join(JoinRelType.INNER, plain.literal(true))
                .project(plain.field(0), plain.field(1),
                        plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.field(1)))
                .join(JoinRelType.LEFT, plain.and(
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(2, 1, 2), plain.field(2, 0, 0)),
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 1, 0), plain.field(2, 1, 1))))
                .project(plain.field(0), plain.field(1), plain.field(3))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var outerInner = base.scan(l)
                .scan(il).scan(ir)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.field(0), builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(1)))
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 1), builder.field(2, 0, 0))))
                .build();
        RuleChecks.doesNotApply(rule, outerInner);

        var wrongProjection = base.scan(l)
                .scan(il).scan(ir)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.field(1), builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(1)))
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 1), builder.field(2, 0, 0))))
                .build();
        RuleChecks.doesNotApply(rule, wrongProjection);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProjectInnerJoin test...");
        runTest();
    }
}
