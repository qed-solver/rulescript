package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRule;
import org.qed.RRuleInstances.ObjectSerializerPruning;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public final class ObjectSerializerPruningTest {
    public static void runTest() {
        var rule = new ObjectSerializerPruning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var before = ordinaryTablePlan(false);
        var after = ordinaryTablePlan(true);
        RuleChecks.rewrites(rule, before, after);
    }

    private static org.apache.calcite.rel.RelNode ordinaryTablePlan(boolean pruned) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("Obj_Type", true), false)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();
        var rexBuilder = builder.getRexBuilder();
        var object = rexBuilder.makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0);
        var ser0 = rexBuilder.makeCall(builder.genericProjectionOp("ser0", RelType.fromString("S0_Type", true)), List.of(object));
        var ser1 = rexBuilder.makeCall(builder.genericProjectionOp("ser1", RelType.fromString("S1_Type", true)), List.of(object));
        var ser2 = rexBuilder.makeCall(builder.genericProjectionOp("ser2", RelType.fromString("S2_Type", true)), List.of(object));
        var serializer = LogicalProject.create(source, List.of(), pruned ? List.of(ser0, ser2)
                : List.of(ser0, ser1, ser2), pruned ? List.of("$f0", "$f1") : List.of("$f0", "$f1", "$f2"));
        var first = rexBuilder.makeInputRef(serializer, 0);
        var second = rexBuilder.makeInputRef(serializer, pruned ? 1 : 2);
        if (pruned) {
            return serializer;
        }
        return LogicalProject.create(serializer, List.of(), List.of(first, second), List.of("$f0", "$f1"));
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
