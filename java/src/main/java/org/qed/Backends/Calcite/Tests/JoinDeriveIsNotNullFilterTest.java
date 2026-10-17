package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinDeriveIsNotNullFilter;
import org.qed.RuleBuilder;

public class JoinDeriveIsNotNullFilterTest {
    public static void runTest() {
        var rule = new JoinDeriveIsNotNullFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsHandBuiltNoMatch();
    }

    private static void rewritesHandBuiltPlan() {
        var schema = RuleBuilder.create();
        var left = RuleChecks.table(schema, 1, false);
        var right = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.ruleBuilder(schema);
        var input = join(b, left, right, false);

        var expectedSchema = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedSchema, 1, false);
        var expectedRight = RuleChecks.table(expectedSchema, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSchema);
        var expected = expectedJoin(expectedBuilder, expectedLeft, expectedRight);

        var planner = CalciteTester.loadRule(RuleChecks.generated("JoinDeriveIsNotNullFilter"), 1);
        planner.setRoot(input);
        var actual = planner.findBestExp();
        check(actual.deepEquals(expected) || actual.explain().equals(expected.explain()),
                "hand-built positive plan did not derive both side filters\nactual:\n" + actual.explain()
                        + "expected:\n" + expected.explain());
        System.out.println("succeeded (hand-built non-null derivation)");
    }

    private static void rejectsHandBuiltNoMatch() {
        var schema = RuleBuilder.create();
        var left = RuleChecks.table(schema, 1, false);
        var right = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.ruleBuilder(schema);
        RuleChecks.doesNotApply(new JoinDeriveIsNotNullFilter(), join(b, left, right, true));
    }

    private static RelNode join(RelBuilder b, String left, String right, boolean unequal) {
        b.scan(left).scan(right);
        var equality = b.call(unequal ? SqlStdOperatorTable.NOT_EQUALS : SqlStdOperatorTable.EQUALS,
                b.field(2, 0, 0), b.field(2, 1, 0));
        var marker = b.call(RuleBuilder.create().genericPredicateOp("marker", true),
                b.field(2, 0, 0), b.field(2, 1, 0));
        return b.join(JoinRelType.INNER, b.and(equality, marker)).build();
    }

    private static RelNode expectedJoin(RelBuilder b, String left, String right) {
        b.scan(left).filter(b.isNotNull(b.field(0)));
        b.scan(right).filter(b.isNotNull(b.field(0)));
        return b.join(JoinRelType.INNER, b.and(
                b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0)),
                b.call(RuleBuilder.create().genericPredicateOp("marker", true),
                        b.field(2, 0, 0), b.field(2, 1, 0)))).build();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            CalciteTester.fail(message);
        } else {
            System.out.println("succeeded");
        }
    }

    public static void main(String[] args) {
        System.out.println("Running JoinDeriveIsNotNullFilter test...");
        runTest();
    }
}
