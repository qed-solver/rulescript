package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.DetectJoinContradiction;

public final class DetectJoinContradictionTest {
    public static void runTest() {
        var rule = new DetectJoinContradiction();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false, false);
        var right = RuleChecks.table(builder, 1, false, false);
        var source = manualJoin(builder, left, right, JoinRelType.INNER, true);
        var expected = manualJoin(builder, left, right, JoinRelType.INNER, false);
        RuleChecks.rewrites(rule, source, expected);

        var nearMissBuilder = org.qed.RuleBuilder.create();
        var nearLeft = RuleChecks.table(nearMissBuilder, 1, false, false);
        var nearRight = RuleChecks.table(nearMissBuilder, 1, false, false);
        RuleChecks.doesNotApply(rule, manualJoin(nearMissBuilder, nearLeft, nearRight, JoinRelType.LEFT, true));

        if (CalciteTester.failures() > 0) {
            throw new AssertionError("DetectJoinContradiction had " + CalciteTester.failures() + " failure(s)");
        }
    }

    private static RelNode manualJoin(org.qed.RuleBuilder ruleBuilder, String left, String right,
                                      JoinRelType type, boolean withContradiction) {
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        builder.scan(left);
        builder.scan(right);
        var rex = builder.getRexBuilder();
        var integer = builder.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER);
        var rest = rex.makeCall(SqlStdOperatorTable.LESS_THAN,
                rex.makeInputRef(integer, 0), rex.makeExactLiteral(java.math.BigDecimal.valueOf(7)));
        var condition = withContradiction
                ? rex.makeCall(SqlStdOperatorTable.AND, rex.makeLiteral(false), rest)
                : rex.makeLiteral(false);
        builder.join(type, condition);
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running DetectJoinContradiction test...");
        runTest();
    }
}
