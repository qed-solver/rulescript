package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRuleInstances.ProjectToCalc;
import org.qed.RuleBuilder;

import java.util.List;

public final class ProjectToCalcTest {
    public static void runTest() {
        var rule = new ProjectToCalc();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectToCalc rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 2, false);
        var relBuilder = RuleChecks.ruleBuilder(ruleBuilder).scan(table);
        var input = relBuilder.build();
        var rexBuilder = relBuilder.getRexBuilder();
        var intType = input.getRowType().getFieldList().get(0).getType();
        var x = rexBuilder.makeInputRef(intType, 0);
        var y = rexBuilder.makeInputRef(intType, 1);
        SqlOperator first = RuleBuilder.create().genericProjectionOp("ordinary_e1", org.qed.RelType.fromString("INTEGER", true));
        SqlOperator second = RuleBuilder.create().genericProjectionOp("ordinary_e2", org.qed.RelType.fromString("INTEGER", true));
        var exprs = List.of(rexBuilder.makeCall(first, x, y), rexBuilder.makeCall(second, y, x));
        RelNode before = relBuilder.push(input).project(exprs, List.of(), true).build();
        RelNode after = relBuilder.push(input).filter(rexBuilder.makeLiteral(true))
                .project(exprs, before.getRowType().getFieldNames(), true).build();
        RuleChecks.rewrites(rule, before, after);
        var nearMissBuilder = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissBuilder, 2, false);
        var nearMiss = RuleChecks.ruleBuilder(nearMissBuilder);
        nearMiss.scan(nearMissTable).project(List.of(nearMiss.field(0)), List.of("A"));
        RuleChecks.doesNotApply(rule, nearMiss.build());
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
