package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SemiJoinJoinTranspose;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class SemiJoinJoinTransposeTest {
    public static void runTest() {
        var rule = new SemiJoinJoinTranspose();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        rewritesIndependentConcretePlan();
        rejectsMixedSemijoinKey();
    }

    private static void rewritesIndependentConcretePlan() {
        var rb = org.qed.RuleBuilder.create();
        var xName = table(rb, 1, false, false);
        var yName = table(rb, 1, false, false);
        var zName = table(rb, 1, false, false);
        var b = ruleBuilder(rb);
        var x = b.scan(xName).build();
        var y = b.scan(yName).build();
        var z = b.scan(zName).build();
        var before = join(b, x, y,
                b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, b.getRexBuilder().makeInputRef(x, 0),
                        b.getRexBuilder().makeInputRef(y, 0)), JoinRelType.INNER);
        var source = before;
        before = join(b, source, z,
                b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, b.getRexBuilder().makeInputRef(source, 0),
                        b.getRexBuilder().makeInputRef(z, 0)), JoinRelType.SEMI);
        var pushed = join(b, x, z,
                b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, b.getRexBuilder().makeInputRef(x, 0),
                        b.getRexBuilder().makeInputRef(z, 0)), JoinRelType.SEMI);
        var expected = join(b, pushed, y,
                b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, b.getRexBuilder().makeInputRef(pushed, 0),
                        b.getRexBuilder().makeInputRef(y, 0)), JoinRelType.INNER);
        rewrites(new SemiJoinJoinTranspose(), before, expected);
    }

    private static LogicalJoin join(org.apache.calcite.tools.RelBuilder b, RelNode left, RelNode right,
                                    RexNode condition, JoinRelType type) {
        return LogicalJoin.create(left, right, List.of(), condition, java.util.Set.of(), type);
    }

    private static void rejectsMixedSemijoinKey() {
        var rb = org.qed.RuleBuilder.create();
        var xName = table(rb, 1, false, false);
        var yName = table(rb, 1, false, false);
        var zName = table(rb, 1, false, false);
        var b = ruleBuilder(rb);
        var x = b.scan(xName).build();
        var y = b.scan(yName).build();
        var z = b.scan(zName).build();
        var lower = join(b, x, y, b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(x, 0), b.getRexBuilder().makeInputRef(y, 0)), JoinRelType.INNER);
        var condition = b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(lower, 1), b.getRexBuilder().makeInputRef(z, 0));
        var mixed = join(b, lower, z, condition, JoinRelType.SEMI);
        doesNotApply(new SemiJoinJoinTranspose(), mixed);
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinJoinTranspose test...");
        runTest();
    }
}
