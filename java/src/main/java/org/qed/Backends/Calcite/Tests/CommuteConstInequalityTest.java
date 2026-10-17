package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CommuteConstInequality;

public class CommuteConstInequalityTest {
    public static void runTest() {
        var rule = new CommuteConstInequality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesConcreteJoin();
        rejectsLeftJoin();
    }

    private static void rewritesConcreteJoin() {
        var tables = org.qed.RuleBuilder.create();
        var leftTable = tables.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        var rightTable = tables.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        tables.addTable(leftTable).addTable(rightTable);
        var left = leftTable.getName();
        var right = rightTable.getName();
        var beforeBuilder = RuleChecks.ruleBuilder(tables);
        beforeBuilder.scan(left).scan(right).join(JoinRelType.INNER, beforeBuilder.literal(true));
        var beforeCondition = beforeBuilder.call(SqlStdOperatorTable.LESS_THAN,
                beforeBuilder.field(0), beforeBuilder.field(1));
        RelNode before = LogicalFilter.create(beforeBuilder.build(), beforeCondition);
        var afterTables = org.qed.RuleBuilder.create();
        afterTables.addTable(leftTable).addTable(rightTable);
        var afterBuilder = RuleChecks.ruleBuilder(afterTables);
        afterBuilder.scan(left).scan(right).join(JoinRelType.INNER, afterBuilder.literal(true));
        var afterCondition = afterBuilder.call(SqlStdOperatorTable.GREATER_THAN,
                afterBuilder.field(1), afterBuilder.field(0));
        RelNode after = LogicalFilter.create(afterBuilder.build(), afterCondition);
        RuleChecks.rewrites(new CommuteConstInequality(), before, after);
    }

    private static void rejectsLeftJoin() {
        var tables = org.qed.RuleBuilder.create();
        var leftTable = tables.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        var rightTable = tables.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        tables.addTable(leftTable).addTable(rightTable);
        var left = leftTable.getName();
        var right = rightTable.getName();
        var builder = RuleChecks.ruleBuilder(tables);
        builder.scan(left).scan(right).join(JoinRelType.LEFT, builder.literal(true));
        var condition = builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), builder.field(1));
        RelNode nearMiss = LogicalFilter.create(builder.build(), condition);
        RuleChecks.doesNotApply(new CommuteConstInequality(), nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteConstInequality test...");
        runTest();
    }
}
