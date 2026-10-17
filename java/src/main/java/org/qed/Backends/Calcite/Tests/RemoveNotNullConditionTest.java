package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RemoveNotNullCondition;
import org.qed.RuleBuilder;

public final class RemoveNotNullConditionTest {
    public static void runTest() {
        var rule = new RemoveNotNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveNotNullCondition rule) {
        var schemaBuilder = RuleBuilder.create();
        var table = RuleChecks.table(schemaBuilder, 2, false, false);
        var builder = RuleChecks.ruleBuilder(schemaBuilder);
        var scan = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var notNull = rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rex.makeInputRef(scan, 0));
        var other = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(scan, 1), rex.makeExactLiteral(java.math.BigDecimal.ZERO));
        RelNode before = LogicalFilter.create(LogicalFilter.create(scan, other), notNull);
        RelNode after = LogicalFilter.create(scan, other);
        RuleChecks.rewrites(rule, before, after);

        var nullableSchemaBuilder = RuleBuilder.create();
        var nullableTable = RuleChecks.table(nullableSchemaBuilder, 2, false, true);
        var nullableBuilder = RuleChecks.ruleBuilder(nullableSchemaBuilder);
        var nullableScan = nullableBuilder.scan(nullableTable).build();
        var nullableRex = nullableBuilder.getRexBuilder();
        var nullableNotNull = nullableRex.makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                nullableRex.makeInputRef(nullableScan, 0));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(nullableScan, nullableNotNull));
        System.out.println("succeeded (ordinary non-nullable table rewrites; nullable table rejected)");
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveNotNullCondition test...");
        runTest();
    }
}
