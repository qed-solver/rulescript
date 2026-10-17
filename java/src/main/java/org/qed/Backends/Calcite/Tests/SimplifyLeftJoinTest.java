package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyLeftJoin;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.doesNotApply;
import static org.qed.Backends.Calcite.Tests.RuleChecks.eq;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewrites;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesConcretePredicates;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesPattern;
import static org.qed.Backends.Calcite.Tests.RuleChecks.table;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rejectsNearMisses;

public class SimplifyLeftJoinTest {
    public static void runTest() {
        var rule = new SimplifyLeftJoin();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        handBuiltSelfJoin();
        inequalityJoinDoesNotMatch();
    }

    private static void handBuiltSelfJoin() {
        var builder = RuleBuilder.create();
        var name = table(builder, 1, false, false);
        var input = builder.scan(name).scan(name).join(JoinRelType.LEFT,
                eq(builder, builder.joinFields().get(0), builder.joinFields().get(1))).build();

        var expected = LogicalJoin.create(((LogicalJoin) input).getLeft(), ((LogicalJoin) input).getRight(),
                ((LogicalJoin) input).getHints(), ((LogicalJoin) input).getCondition(),
                ((LogicalJoin) input).getVariablesSet(), JoinRelType.INNER);
        rewrites(new SimplifyLeftJoin(), input, expected);
    }

    private static void inequalityJoinDoesNotMatch() {
        var builder = RuleBuilder.create();
        var name = table(builder, 1, false, false);
        RelNode plan = builder.scan(name).scan(name).join(JoinRelType.LEFT,
                builder.call(SqlStdOperatorTable.NOT_EQUALS,
                        builder.joinFields().get(0), builder.joinFields().get(1))).build();
        doesNotApply(new SimplifyLeftJoin(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyLeftJoin test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("SimplifyLeftJoin test failed: " + CalciteTester.failures());
        }
    }
}
