package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneProjectSetCols;
import org.qed.RuleBuilder;

public class PruneProjectSetColsTest {
    public static void runTest() {
        var rule = new PruneProjectSetCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneProjectSetCols rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var emitted = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(left).scan(emitted);
        var intType = b.getTypeFactory().createSqlType(SqlTypeName.INTEGER);
        var rex = b.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.EQUALS, rex.makeInputRef(intType, 2), rex.makeInputRef(intType, 0));
        var beforeJoin = b.join(JoinRelType.INNER, condition);
        var beforeProjection = rex.makeCall(SqlStdOperatorTable.PLUS, rex.makeInputRef(intType, 0), rex.makeInputRef(intType, 2));
        var before = b.project(beforeProjection).build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 2, false);
        var expectedRows = RuleChecks.table(expectedBuilder, 1, false);
        var eb = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedInput = eb.scan(expectedLeft).project(eb.field(0)).build();
        eb.push(expectedInput).scan(expectedRows);
        var expectedIntType = eb.getTypeFactory().createSqlType(SqlTypeName.INTEGER);
        var expectedRex = eb.getRexBuilder();
        var expectedCondition = expectedRex.makeCall(SqlStdOperatorTable.EQUALS,
                expectedRex.makeInputRef(expectedIntType, 1), expectedRex.makeInputRef(expectedIntType, 0));
        eb.join(JoinRelType.INNER, expectedCondition);
        var afterProjection = expectedRex.makeCall(SqlStdOperatorTable.PLUS,
                expectedRex.makeInputRef(expectedIntType, 0), expectedRex.makeInputRef(expectedIntType, 1));
        var after = eb.project(afterProjection).build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneProjectSetCols RULE test...");
        runTest();
    }
}
