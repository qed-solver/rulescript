package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;

import java.util.List;

public class PushSelectIntoProjectTest {
    public static void runTest() {
        var pattern = new org.qed.RRuleInstances.PushSelectIntoProject();
        var rule = RuleChecks.generated("PushSelectIntoProject");
        RuleChecks.rewritesPattern(pattern);
        RuleChecks.rewritesConcretePredicates(pattern);
        RuleChecks.rejectsNearMisses(pattern);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.apache.calcite.plan.RelOptRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var scan = builder.scan(table).build();
        SqlOperator projection = builder.genericProjectionOp("F", org.qed.RelType.fromString("INTEGER", true));
        SqlOperator p = builder.genericPredicateOp("P", true);
        SqlOperator q = builder.genericPredicateOp("Q", true);
        var rex = builder.getRexBuilder();
        var input = rex.makeInputRef(scan, 0);
        var computed = rex.makeCall(projection, input);
        var project = org.apache.calcite.rel.logical.LogicalProject.create(scan, List.of(), List.of(computed, input), List.of("f", "a"));
        var condition = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                rex.makeCall(p, rex.makeInputRef(project, 1)), rex.makeCall(q, rex.makeInputRef(project, 0)));
        var plan = LogicalFilter.create(project, condition);

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedInput = plain.scan(table).field(0);
        var expectedP = plain.call(builder.genericPredicateOp("P", true), expectedInput);
        var filtered = LogicalFilter.create(plain.peek(), expectedP);
        plain.push(filtered);
        var expectedComputed = plain.call(builder.genericProjectionOp("F", org.qed.RelType.fromString("INTEGER", true)), plain.field(0));
        plain.project(List.of(expectedComputed, plain.field(0)));
        var expectedQ = plain.call(builder.genericPredicateOp("Q", true), plain.field(0));
        var expected = LogicalFilter.create(plain.build(), expectedQ);

        RuleChecks.rewrites(patternRule(), plan, expected);

        var widerTable = RuleChecks.table(builder, 2, false, false);
        var widerScan = builder.scan(widerTable).build();
        var widerInput = rex.makeInputRef(widerScan, 0);
        var nearComputed = rex.makeCall(projection, widerInput);
        var nearProject = org.apache.calcite.rel.logical.LogicalProject.create(widerScan, List.of(), List.of(nearComputed, widerInput), List.of("f", "a"));
        var nearCondition = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                rex.makeCall(p, rex.makeInputRef(nearProject, 1)), rex.makeCall(q, rex.makeInputRef(nearProject, 0)));
        RuleChecks.doesNotApply(patternRule(), LogicalFilter.create(nearProject, nearCondition));
    }

    private static org.qed.RRule patternRule() {
        return new org.qed.RRuleInstances.PushSelectIntoProject();
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoProject test...");
        runTest();
    }
}
