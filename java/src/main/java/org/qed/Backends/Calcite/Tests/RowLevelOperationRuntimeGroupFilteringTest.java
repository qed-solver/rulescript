package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.RowLevelOperationRuntimeGroupFiltering;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class RowLevelOperationRuntimeGroupFilteringTest {
    public static void runTest() {
        var rule = new RowLevelOperationRuntimeGroupFiltering();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var b = ruleBuilder(builder);
        var t = table(builder, 2, false, false);
        b.scan(t);
        var condition = gt(b, b.field(1), 7);
        b.filter(condition);
        var delta = b.build();

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedBuilder = ruleBuilder(expectedRuleBuilder);
        table(expectedRuleBuilder, 2, false, false);
        expectedBuilder.scan(t);
        var left = expectedBuilder.build();
        expectedBuilder.scan(t);
        expectedBuilder.filter(gt(expectedBuilder, expectedBuilder.field(1), 7));
        var right = expectedBuilder.build();
        expectedBuilder.push(left);
        expectedBuilder.push(right);
        var joinCondition = expectedBuilder.getRexBuilder().makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                expectedBuilder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                expectedBuilder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 2));
        expectedBuilder.join(JoinRelType.SEMI, joinCondition);
        expectedBuilder.filter(gt(expectedBuilder, expectedBuilder.field(1), 7));
        var expected = expectedBuilder.build();
        rewrites(rule, delta, expected);

        var nearMissRuleBuilder = RuleBuilder.create();
        var nearMiss = ruleBuilder(nearMissRuleBuilder);
        var nearMissTable = table(nearMissRuleBuilder, 2, false, true);
        nearMiss.scan(nearMissTable);
        nearMiss.filter(gt(nearMiss, nearMiss.field(1), 8));
        doesNotApply(rule, nearMiss.build());
    }

    public static void main(String[] args) {
        System.out.println("Running RowLevelOperationRuntimeGroupFiltering test...");
        runTest();
    }
}
