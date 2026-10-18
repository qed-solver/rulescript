package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneSelectCols;
import org.qed.RuleBuilder;

public class PruneSelectColsTest {
    public static void runTest() {
        var rule = new PruneSelectCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneSelectCols rule) {
        var sourceBuilder = RuleBuilder.create();
        var table = RuleChecks.table(sourceBuilder, 3, false);
        var b = RuleChecks.ruleBuilder(sourceBuilder);
        var input = b.scan(table).build();
        b.push(input);
        var x = b.field(0);
        var y = b.field(1);
        var condition = b.call(SqlStdOperatorTable.AND,
                RuleChecks.gt(b, x, 10), RuleChecks.gt(b, y, 20));
        var before = b.filter(condition).project(
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build();

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 3, false);
        var e = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedInput = e.scan(expectedTable).project(e.field(0), e.field(1)).build();
        e.push(expectedInput);
        var expectedCondition = e.call(SqlStdOperatorTable.AND,
                RuleChecks.gt(e, e.field(0), 10), RuleChecks.gt(e, e.field(1), 20));
        var after = e.filter(expectedCondition).project(
                e.call(SqlStdOperatorTable.PLUS, e.field(0), e.field(1))).build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
