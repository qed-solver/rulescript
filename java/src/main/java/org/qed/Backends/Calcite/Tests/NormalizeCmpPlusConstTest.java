package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRule;
import org.qed.RRuleInstances.NormalizeCmpPlusConst;
import org.qed.RuleBuilder;

public class NormalizeCmpPlusConstTest {
    public static void runTest() {
        var rule = new NormalizeCmpPlusConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 3, false, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var source = builder.scan(table).build();
        var fields = source.getRowType().getFieldList();
        var a = builder.getRexBuilder().makeInputRef(fields.get(0).getType(), 0);
        var c1 = builder.getRexBuilder().makeInputRef(fields.get(1).getType(), 1);
        var c2 = builder.getRexBuilder().makeInputRef(fields.get(2).getType(), 2);
        var sum = builder.call(SqlStdOperatorTable.PLUS, a, c1);
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(source,
                builder.call(SqlStdOperatorTable.LESS_THAN, sum, c2));

        var expectedQedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedQedBuilder, 3, false, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedQedBuilder);
        var expectedSource = expectedBuilder.scan(expectedTable).build();
        var expectedFields = expectedSource.getRowType().getFieldList();
        var expectedA = expectedBuilder.getRexBuilder().makeInputRef(expectedFields.get(0).getType(), 0);
        var expectedC1 = expectedBuilder.getRexBuilder().makeInputRef(expectedFields.get(1).getType(), 1);
        var expectedC2 = expectedBuilder.getRexBuilder().makeInputRef(expectedFields.get(2).getType(), 2);
        var difference = expectedBuilder.call(SqlStdOperatorTable.MINUS, expectedC2, expectedC1);
        var after = org.apache.calcite.rel.logical.LogicalFilter.create(expectedSource,
                expectedBuilder.call(SqlStdOperatorTable.LESS_THAN, expectedA, difference));
        RuleChecks.rewrites(rule, before, after);

        var nearQedBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearQedBuilder, 3, false, false);
        var nearBuilder = RuleChecks.ruleBuilder(nearQedBuilder);
        var nearSource = nearBuilder.scan(nearTable).build();
        var nearFields = nearSource.getRowType().getFieldList();
        var nearA = nearBuilder.getRexBuilder().makeInputRef(nearFields.get(0).getType(), 0);
        var nearC1 = nearBuilder.getRexBuilder().makeInputRef(nearFields.get(1).getType(), 1);
        var nearC2 = nearBuilder.getRexBuilder().makeInputRef(nearFields.get(2).getType(), 2);
        var nearSum = nearBuilder.call(SqlStdOperatorTable.PLUS, nearA, nearC1);
        var nearMiss = org.apache.calcite.rel.logical.LogicalFilter.create(nearSource,
                nearBuilder.call(SqlStdOperatorTable.GREATER_THAN, nearSum, nearC2));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
