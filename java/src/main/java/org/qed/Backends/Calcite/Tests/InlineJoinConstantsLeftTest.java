package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.InlineJoinConstantsLeft;
import org.qed.RuleBuilder;

public final class InlineJoinConstantsLeftTest {
    public static void runTest() {
        var rule = new InlineJoinConstantsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsHandBuiltOuterJoin();
    }

    private static void rewritesHandBuiltPlan() {
        var before = handBuiltPlan(JoinRelType.INNER, false);
        var expected = handBuiltPlan(JoinRelType.INNER, true);
        RuleChecks.rewrites(new InlineJoinConstantsLeft(), before, expected);
    }

    private static void rejectsHandBuiltOuterJoin() {
        var plan = handBuiltPlan(JoinRelType.LEFT, false);
        RuleChecks.doesNotApply(new InlineJoinConstantsLeft(), plan);
    }

    private static RelNode handBuiltPlan(JoinRelType outerType, boolean useConstantInOuterPredicate) {
        RuleBuilder schema = RuleBuilder.create();
        var sourceName = RuleChecks.table(schema, 1, false);
        var constName = RuleChecks.table(schema, 1, true, false);
        var rightName = RuleChecks.table(schema, 1, false);
        RelBuilder b = schema.transform(CalciteRuntime::configure);
        var source = b.scan(sourceName).build();
        var constant = b.scan(constName).build();
        var right = b.scan(rightName).build();
        b.push(source).push(constant);
        var eq = b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0));
        var left = b.join(JoinRelType.INNER, eq).build();
        b.push(left).push(right);
        var leftOrdinal = useConstantInOuterPredicate ? 1 : 0;
        var predicate = b.call(SqlStdOperatorTable.EQUALS,
                b.field(2, 0, leftOrdinal), b.field(2, 1, 0));
        return b.join(outerType, predicate).build();
    }

    public static void main(String[] args) {
        System.out.println("Running InlineJoinConstantsLeft test...");
        runTest();
    }
}
