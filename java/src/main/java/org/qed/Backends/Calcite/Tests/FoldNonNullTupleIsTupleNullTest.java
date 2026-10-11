package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNonNullTupleIsTupleNull;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class FoldNonNullTupleIsTupleNullTest {

    public static void runTest() {
        var rule = new FoldNonNullTupleIsTupleNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        tupleColumns(rule);
    }

    private static void tupleColumns(FoldNonNullTupleIsTupleNull rule) {
        var builder = RuleBuilder.create();
        var schema = Seq.of(
                Tuple.of(RelType.fromString("INTEGER", false), false),
                Tuple.of(RelType.fromString("INTEGER", true), false));
        var qedTable = builder.createQedTable(schema);
        builder.addTable(qedTable);
        var table = qedTable.getName();
        var bothNullable = RuleChecks.table(builder, 2, false, true);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)),
                        builder.call(SqlStdOperatorTable.IS_NULL, builder.field(1))))
                .build();
        var after = plain.scan(table).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var firstOnly = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, firstOnly);

        var nullableFirst = builder.scan(bothNullable)
                .filter(builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)),
                        builder.call(SqlStdOperatorTable.IS_NULL, builder.field(1))))
                .build();
        RuleChecks.doesNotApply(rule, nullableFirst);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullTupleIsTupleNull test...");
        runTest();
    }
}
