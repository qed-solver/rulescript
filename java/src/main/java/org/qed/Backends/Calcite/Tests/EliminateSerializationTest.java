package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRuleInstances.EliminateSerialization;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public final class EliminateSerializationTest {
    public static void runTest() {
        var rule = new EliminateSerialization();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
    }

    private static void rewritesHandBuiltPlan(EliminateSerialization rule) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("RowType", true), false)));
        builder.addTable(table);
        var input = builder.scan(table.getName()).build();
        var rex = builder.getRexBuilder();
        SqlOperator deserialize = builder.genericProjectionOp("deserialize", RelType.fromString("ObjType", true));
        SqlOperator predicate = builder.genericPredicateOp("matches", true);
        RexNode deserialized = rex.makeCall(deserialize, rex.makeInputRef(input, 0));
        RexNode condition = rex.makeCall(predicate, deserialized);
        RelNode filtered = LogicalFilter.create(input, condition);
        RelNode before = LogicalProject.create(filtered, List.of(), List.of(deserialized), List.of("object"));
        RelNode projected = LogicalProject.create(input, List.of(), List.of(deserialized), List.of("object"));
        RelNode expected = LogicalFilter.create(projected, rex.makeCall(predicate, rex.makeInputRef(projected, 0)));
        RuleChecks.rewrites(rule, before, expected);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSerialization test...");
        runTest();
    }
}
