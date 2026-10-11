package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyIsNullCondition;
import org.qed.RuleBuilder;

public class SimplifyIsNullConditionTest {

    public static void runTest() {
        var rule = new SimplifyIsNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notNullColumn(rule);
    }

    private static void notNullColumn(SimplifyIsNullCondition rule) {
        var base = RuleBuilder.create();
        var table = mixedTable(base);
        var b = RuleChecks.ruleBuilder(base);
        var scan = b.scan(table).build();
        var rex = b.getRexBuilder();
        var gt = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(scan, 1), b.literal(5));
        var before = LogicalFilter.create(LogicalFilter.create(scan, gt),
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(scan, 0)));
        var after = b.scan(table).filter(b.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var nullableScan = base.scan(table).build();
        var nullableRex = base.getRexBuilder();
        var nullableColumn = LogicalFilter.create(
                LogicalFilter.create(nullableScan, nullableRex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                        nullableRex.makeInputRef(nullableScan, 0), base.literal(5))),
                nullableRex.makeCall(SqlStdOperatorTable.IS_NULL, nullableRex.makeInputRef(nullableScan, 1)));
        RuleChecks.doesNotApply(rule, nullableColumn);
    }

    private static String mixedTable(RuleBuilder builder) {
        var schema = new java.util.ArrayList<kala.tuple.Tuple2<org.qed.RelType, Boolean>>();
        schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", false), false));
        schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false));
        var table = builder.createQedTable(kala.collection.Seq.from(schema));
        builder.addTable(table);
        return table.getName();
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIsNullCondition test...");
        runTest();
    }
}
