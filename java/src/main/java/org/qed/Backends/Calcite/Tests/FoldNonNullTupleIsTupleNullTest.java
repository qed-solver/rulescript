package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.logical.LogicalFilter;
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
        var rex = plain.getRexBuilder();
        var scan = plain.scan(table).build();
        var before = LogicalFilter.create(scan, rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(scan, 0)),
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(scan, 1))));
        var after = plain.scan(table).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var firstOnly = LogicalFilter.create(scan, rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(scan, 0)));
        RuleChecks.doesNotApply(rule, firstOnly);

        var nullScan = plain.scan(bothNullable).build();
        var nullableFirst = LogicalFilter.create(nullScan, rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(nullScan, 0)),
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(nullScan, 1))));
        RuleChecks.doesNotApply(rule, nullableFirst);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullTupleIsTupleNull test...");
        runTest();
    }
}
