package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AssociateLimitJoinsLeft;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class AssociateLimitJoinsLeftTest {
    public static void runTest() {
        var rule = new AssociateLimitJoinsLeft();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsLeftJoinRoot();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var a = table(builder, 1, false);
        var b = table(builder, 1, false);
        var c = table(builder, 1, false);
        var rel = ruleBuilder(builder);
        RelNode scanA = rel.scan(a).build();
        RelNode scanB = rel.scan(b).build();
        RelNode scanC = rel.scan(c).build();
        var actual = join(rel, scanA, scanB, scanC, false, false);

        var expectedBuilder = RuleBuilder.create();
        var ea = table(expectedBuilder, 1, false);
        var eb = table(expectedBuilder, 1, false);
        var ec = table(expectedBuilder, 1, false);
        var expectedRel = ruleBuilder(expectedBuilder);
        RelNode expectedA = expectedRel.scan(ea).build();
        RelNode expectedB = expectedRel.scan(eb).build();
        RelNode expectedC = expectedRel.scan(ec).build();
        var expected = join(expectedRel, expectedA, expectedB, expectedC, false, true);

        rewrites(new AssociateLimitJoinsLeft(), actual, expected);
    }

    private static void rejectsLeftJoinRoot() {
        var builder = RuleBuilder.create();
        var a = table(builder, 1, false);
        var b = table(builder, 1, false);
        var c = table(builder, 1, false);
        var rel = ruleBuilder(builder);
        RelNode scanA = rel.scan(a).build();
        RelNode scanB = rel.scan(b).build();
        RelNode scanC = rel.scan(c).build();
        var nearMiss = join(rel, scanA, scanB, scanC, true);
        doesNotApply(new AssociateLimitJoinsLeft(), nearMiss);
    }

    private static RelNode join(RelBuilder builder, RelNode a, RelNode b, RelNode c, boolean leftRoot) {
        return join(builder, a, b, c, leftRoot, false);
    }

    private static RelNode join(RelBuilder builder, RelNode a, RelNode b, RelNode c,
                                boolean leftRoot, boolean expected) {
        var abType = a.getRowType().getFieldList().get(0).getType();
        var rex = builder.getRexBuilder();
        var abCond = rex.makeCall(SqlStdOperatorTable.EQUALS,
                new RexInputRef(0, abType), new RexInputRef(1, abType));
        if (expected) {
            var acInnerCond = rex.makeCall(SqlStdOperatorTable.LESS_THAN,
                    new RexInputRef(0, abType), new RexInputRef(1, abType));
            var ac = org.apache.calcite.rel.logical.LogicalJoin.create(a, c, java.util.List.of(), acInnerCond,
                    java.util.Set.of(), JoinRelType.INNER);
            var abAfterCond = rex.makeCall(SqlStdOperatorTable.EQUALS,
                    new RexInputRef(0, abType), new RexInputRef(2, abType));
            var acb = org.apache.calcite.rel.logical.LogicalJoin.create(ac, b, java.util.List.of(), abAfterCond,
                    java.util.Set.of(), JoinRelType.LEFT);
            return org.apache.calcite.rel.logical.LogicalProject.create(acb, java.util.List.of(),
                    java.util.List.of(new RexInputRef(0, acb.getRowType().getFieldList().get(0).getType()),
                            new RexInputRef(2, acb.getRowType().getFieldList().get(2).getType()),
                            new RexInputRef(1, acb.getRowType().getFieldList().get(1).getType())),
                    java.util.List.of("A", "B", "C"));
        }
        var ab = org.apache.calcite.rel.logical.LogicalJoin.create(a, b, java.util.List.of(), abCond,
                java.util.Set.of(), JoinRelType.LEFT);
        var acCond = rex.makeCall(SqlStdOperatorTable.LESS_THAN,
                new RexInputRef(0, abType), new RexInputRef(2, abType));
        var root = org.apache.calcite.rel.logical.LogicalJoin.create(ab, c, java.util.List.of(), acCond,
                java.util.Set.of(), leftRoot ? JoinRelType.LEFT : JoinRelType.INNER);
        return root;
    }

    public static void main(String[] args) {
        System.out.println("Running AssociateLimitJoinsLeft test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("Calcite tests failed: " + CalciteTester.failures());
        }
    }
}
