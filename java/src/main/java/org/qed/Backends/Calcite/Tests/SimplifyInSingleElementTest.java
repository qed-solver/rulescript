package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.Backends.Calcite.Tests.RuleChecks;
import org.qed.RRuleInstances.SimplifyInSingleElement;
import org.qed.RuleBuilder;

public class SimplifyInSingleElementTest {
    public static void runTest() {
        var rule = new SimplifyInSingleElement();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        rejectsMultiArmOr(rule);
    }

    private static void handBuiltMatch(SimplifyInSingleElement rule) {
        var schema = RuleBuilder.create();
        var table = RuleChecks.table(schema, 2, false, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var scan = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var left = rex.makeInputRef(scan, 0);
        var right = rex.makeInputRef(scan, 1);
        var equality = rex.makeCall(SqlStdOperatorTable.EQUALS, left, right);
        var singleArmOr = rex.makeCall(SqlStdOperatorTable.OR, equality);
        var input = LogicalFilter.create(scan, singleArmOr);
        var expected = LogicalFilter.create(scan, equality);
        RuleChecks.rewrites(rule, input, expected);
    }

    private static void rejectsMultiArmOr(SimplifyInSingleElement rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var scan = relBuilder.scan(table).build();
        var rex = relBuilder.getRexBuilder();
        var left = rex.makeInputRef(scan, 0);
        var right = rex.makeInputRef(scan, 1);
        var equality = rex.makeCall(SqlStdOperatorTable.EQUALS, left, right);
        var otherArm = rex.makeCall(SqlStdOperatorTable.NOT_EQUALS, left, right);
        RelNode nearMiss = LogicalFilter.create(scan, rex.makeCall(SqlStdOperatorTable.OR, equality, otherArm));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyInSingleElement test...");
        runTest();
    }
}
