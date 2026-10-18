package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PropagateEmptyRelation;
import org.qed.RuleBuilder;

public class PropagateEmptyRelationTest {
    public static void runTest() {
        var rule = new PropagateEmptyRelation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PropagateEmptyRelation rule) {
        var tables = RuleBuilder.create();
        var left = RuleChecks.table(tables, 2, false, false);
        var right = RuleChecks.table(tables, 2, false, false);
        var builder = RuleChecks.ruleBuilder(tables);
        var plan = builder.scan(left).scan(right).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
