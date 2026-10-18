package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.InlineJoinConstantsRight;
import org.qed.RuleBuilder;

public class InlineJoinConstantsRightTest {
    public static void runTest() {
        var rule = new InlineJoinConstantsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsNonUniqueConstantRelation();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var sourceName = RuleChecks.table(builder, 1, false, false);
        var constName = RuleChecks.table(builder, 1, true, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(leftName).build();
        RelNode source = b.scan(sourceName).build();
        RelNode constant = b.scan(constName).build();
        b.push(left);
        b.push(source);
        b.push(constant);
        var variableEqualsConstant = b.call(SqlStdOperatorTable.EQUALS,
                b.field(2, 0, 0), b.field(2, 1, 0));
        b.join(JoinRelType.INNER, variableEqualsConstant);
        RelNode constrainedRight = b.build();
        b.push(left);
        b.push(constrainedRight);
        var beforePredicate = b.call(SqlStdOperatorTable.IS_NOT_NULL, b.field(2, 1, 0));
        b.join(JoinRelType.INNER, beforePredicate);
        RelNode before = b.build();

        b.push(left);
        b.push(source);
        b.push(constant);
        b.join(JoinRelType.INNER,
                b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0)));
        RelNode expectedRight = b.build();
        b.push(left);
        b.push(expectedRight);
        b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.IS_NOT_NULL, b.field(2, 1, 1)));
        RelNode expected = b.build();
        var rule = new InlineJoinConstantsRight();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsNonUniqueConstantRelation() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var sourceName = RuleChecks.table(builder, 1, false, false);
        var constName = RuleChecks.table(builder, 1, false, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        b.scan(leftName);
        b.scan(sourceName);
        b.scan(constName);
        var equality = b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0));
        b.join(JoinRelType.INNER, equality);
        var predicate = b.call(SqlStdOperatorTable.IS_NOT_NULL, b.field(2, 1, 0));
        b.join(JoinRelType.INNER, predicate);
        RelNode plan = b.build();
        RuleChecks.doesNotApply(new InlineJoinConstantsRight(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineJoinConstantsRight test...");
        runTest();
    }
}
