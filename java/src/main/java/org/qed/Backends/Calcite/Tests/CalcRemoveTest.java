package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.CalcRemove;
import org.qed.RuleBuilder;

import java.util.List;

public class CalcRemoveTest {

    public static void runTest() {
        var rule = new CalcRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CalcRemove rule) {
        var base = RuleBuilder.create();
        var two = RuleChecks.table(base, 2, false);
        var three = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(two).project(List.of(b.field(0), b.field(1)), List.of(), true).build();
        var after = b.scan(two).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(two).project(List.of(b.field(1), b.field(0)), List.of(), true).build());
        RuleChecks.doesNotApply(rule,
                b.scan(three).project(List.of(b.field(0), b.field(1), b.field(2)), List.of(), true).build());
    }

    public static void main(String[] args) {
        System.out.println("Running CalcRemove test...");
        runTest();
    }
}
