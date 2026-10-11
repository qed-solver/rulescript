package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterToCalc;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterToCalcTest {

    public static void runTest() {
        var rule = new FilterToCalc();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterToCalc rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var filter = b.scan(t).filter(RuleChecks.gt(b, b.field(0), 5)).build();
        RuleChecks.rewrites(rule, filter, b.scan(t).filter(RuleChecks.gt(b, b.field(0), 5)).build());

        RuleChecks.doesNotApply(rule, b.scan(t).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterToCalc test...");
        runTest();
    }
}
