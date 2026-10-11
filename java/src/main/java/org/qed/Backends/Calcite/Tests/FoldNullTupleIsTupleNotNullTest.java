package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullTupleIsTupleNotNull;
import org.qed.RuleBuilder;

public class FoldNullTupleIsTupleNotNullTest {

    public static void runTest() {
        var rule = new FoldNullTupleIsTupleNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullElementTuple(rule);
    }

    private static void nullElementTuple(FoldNullTupleIsTupleNotNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var rex = plain.getRexBuilder();
        var nullInt = rex.makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var scan = plain.scan(table).build();
        var notNullNull = rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nullInt);
        var notNull0 = rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rex.makeInputRef(scan, 0));
        var condition = rex.makeCall(SqlStdOperatorTable.AND, notNullNull, notNull0);
        var before = LogicalFilter.create(scan, condition);
        var after = plain.scan(table).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var noNullElement = LogicalFilter.create(scan, notNull0);
        RuleChecks.doesNotApply(rule, noNullElement);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullTupleIsTupleNotNull test...");
        runTest();
    }
}
