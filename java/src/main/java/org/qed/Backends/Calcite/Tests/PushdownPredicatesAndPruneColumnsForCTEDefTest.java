package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PushdownPredicatesAndPruneColumnsForCTEDef;
import org.qed.RuleBuilder;

import java.util.List;

public class PushdownPredicatesAndPruneColumnsForCTEDefTest {
    public static void runTest() {
        var rule = new PushdownPredicatesAndPruneColumnsForCTEDef();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushdownPredicatesAndPruneColumnsForCTEDef rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var scan = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var field = rex.makeInputRef(scan, 0);
        var first = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, field, rex.makeExactLiteral(java.math.BigDecimal.valueOf(3)));
        var second = rex.makeCall(SqlStdOperatorTable.LESS_THAN, field, rex.makeExactLiteral(java.math.BigDecimal.valueOf(9)));
        var plan = LogicalUnion.create(List.of(
                LogicalFilter.create(scan, first),
                LogicalFilter.create(scan, second)), true);

        RelBuilder plain = RuleChecks.ruleBuilder(builder);
        RelNode plainScan = plain.scan(table).build();
        var plainRex = plain.getRexBuilder();
        RexNode plainField = plainRex.makeInputRef(plainScan, 0);
        RexNode plainFirst = plainRex.makeCall(SqlStdOperatorTable.GREATER_THAN, plainField,
                plainRex.makeExactLiteral(java.math.BigDecimal.valueOf(3)));
        RexNode plainSecond = plainRex.makeCall(SqlStdOperatorTable.LESS_THAN, plainField,
                plainRex.makeExactLiteral(java.math.BigDecimal.valueOf(9)));
        RexNode combined = plainRex.makeCall(SqlStdOperatorTable.OR, plainFirst, plainSecond);
        RelNode pushedDef = LogicalFilter.create(plainScan, combined);
        RelNode expected = LogicalUnion.create(List.of(
                LogicalFilter.create(pushedDef, plainFirst),
                LogicalFilter.create(pushedDef, plainSecond)), true);
        RuleChecks.rewrites(rule, plan, expected);

        var nearMiss = LogicalUnion.create(List.of(
                LogicalFilter.create(scan, first),
                LogicalFilter.create(scan, second)), false);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushdownPredicatesAndPruneColumnsForCTEDef test...");
        runTest();
    }
}
