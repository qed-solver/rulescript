package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifySameVarInequalities;
import org.qed.RuleBuilder;

public class SimplifySameVarInequalitiesTest {
    public static void runTest() {
        var rule = new SimplifySameVarInequalities();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifySameVarInequalities rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var input = builder.scan(table).build();
        var field = builder.getRexBuilder().makeInputRef(input, 0);
        var before = LogicalFilter.create(input,
                builder.call(SqlStdOperatorTable.NOT_EQUALS, field, field));
        var plain = RuleChecks.ruleBuilder(builder);
        var expectedInput = plain.scan(table).build();
        var expectedField = plain.getRexBuilder().makeInputRef(expectedInput, 0);
        var nullLiteral = plain.getRexBuilder().makeNullLiteral(expectedField.getType());
        var expectedCondition = plain.call(SqlStdOperatorTable.AND,
                plain.call(SqlStdOperatorTable.IS_NULL, expectedField), nullLiteral);
        var after = LogicalFilter.create(expectedInput, expectedCondition);
        RuleChecks.rewrites(rule, before, after);

        var nearMissBuilder = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissBuilder, 1, false, true);
        var nearMissInput = nearMissBuilder.scan(nearMissTable).build();
        var x = nearMissBuilder.getRexBuilder().makeInputRef(nearMissInput, 0);
        var different = nearMissBuilder.literal(4);
        var nearMiss = LogicalFilter.create(nearMissInput,
                nearMissBuilder.call(SqlStdOperatorTable.NOT_EQUALS, x, different));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifySameVarInequalities test...");
        runTest();
    }
}
