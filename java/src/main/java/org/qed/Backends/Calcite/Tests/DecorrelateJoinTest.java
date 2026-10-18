package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.DecorrelateJoin;
import org.qed.RuleBuilder;

public class DecorrelateJoinTest {
    public static void runTest() {
        var rule = new DecorrelateJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesLessThanCorrelate();
        rejectsLeftCorrelate();
    }

    private static void rewritesLessThanCorrelate() {
        var rules = RuleBuilder.create();
        var leftTable = RuleChecks.table(rules, 1, false, false);
        var rightTable = RuleChecks.table(rules, 1, false, false);
        var builder = RuleChecks.ruleBuilder(rules);
        var left = builder.scan(leftTable).build();
        var right = builder.scan(rightTable).build();
        var before = RuleChecks.correlate(builder, left, right, JoinRelType.INNER,
                fields -> builder.call(SqlStdOperatorTable.LESS_THAN, fields[0], fields[1]));
        var after = builder.scan(leftTable).scan(rightTable).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        RuleChecks.rewrites(new DecorrelateJoin(), before, after);
    }

    private static void rejectsLeftCorrelate() {
        var rules = RuleBuilder.create();
        var leftTable = RuleChecks.table(rules, 1, false, false);
        var rightTable = RuleChecks.table(rules, 1, false, false);
        var builder = RuleChecks.ruleBuilder(rules);
        var left = builder.scan(leftTable).build();
        var right = builder.scan(rightTable).build();
        var plan = RuleChecks.correlate(builder, left, right, JoinRelType.LEFT,
                fields -> builder.call(SqlStdOperatorTable.LESS_THAN, fields[0], fields[1]));
        RuleChecks.doesNotApply(new DecorrelateJoin(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running DecorrelateJoin test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
