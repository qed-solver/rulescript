package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ConstantPropagation;

import java.util.List;

public class ConstantPropagationTest {
    public static void runTest() {
        var rule = new ConstantPropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlans();
        rejectsLeftJoin();
    }

    private static void rewritesHandBuiltPlans() {
        var builder = org.qed.RuleBuilder.create();
        var sourceName = RuleChecks.table(builder, 1, false);
        var constName = RuleChecks.table(builder, 1, true, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var source = rel.scan(sourceName).build();
        var constant = rel.scan(constName).build();
        var joined = rel.push(source).push(constant).join(JoinRelType.INNER,
                rel.call(SqlStdOperatorTable.EQUALS, rel.field(2, 0, 0), rel.field(2, 1, 0))).build();
        var before = rel.push(joined).filter(
                rel.call(SqlStdOperatorTable.AND,
                        rel.call(SqlStdOperatorTable.EQUALS, rel.field(0), rel.field(1)),
                        rel.call(SqlStdOperatorTable.LESS_THAN, rel.field(0), rel.literal(7)))).build();
        var expected = rel.push(source).push(constant).join(JoinRelType.INNER,
                rel.call(SqlStdOperatorTable.EQUALS, rel.field(2, 0, 0), rel.field(2, 1, 0))).build();
        var expectedPlan = rel.push(expected).filter(
                rel.call(SqlStdOperatorTable.AND,
                        rel.call(SqlStdOperatorTable.EQUALS, rel.field(0), rel.field(1)),
                        rel.call(SqlStdOperatorTable.LESS_THAN, rel.field(1), rel.literal(7)))).build();
        RuleChecks.rewrites(new ConstantPropagation(), before, expectedPlan);
    }

    private static void rejectsLeftJoin() {
        var builder = org.qed.RuleBuilder.create();
        var sourceName = RuleChecks.table(builder, 1, false);
        var constName = RuleChecks.table(builder, 1, true, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var left = rel.scan(sourceName).build();
        var right = rel.scan(constName).build();
        RelNode plan = rel.push(left).push(right).join(JoinRelType.LEFT,
                rel.call(SqlStdOperatorTable.EQUALS, rel.field(2, 0, 0), rel.field(2, 1, 0))).build();
        plan = rel.push(plan).filter(rel.call(SqlStdOperatorTable.AND,
                rel.call(SqlStdOperatorTable.EQUALS, rel.field(0), rel.field(1)),
                rel.call(SqlStdOperatorTable.LESS_THAN, rel.field(0), rel.literal(7)))).build();
        RuleChecks.doesNotApply(new ConstantPropagation(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running ConstantPropagation test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("ConstantPropagation test failures: " + CalciteTester.failures());
        }
    }
}
