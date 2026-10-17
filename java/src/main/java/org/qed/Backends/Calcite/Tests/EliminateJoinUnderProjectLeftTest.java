package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RelRN;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.EliminateJoinUnderProjectLeft;

import java.util.List;

public class EliminateJoinUnderProjectLeftTest {
    public static void runTest() {
        var rule = new EliminateJoinUnderProjectLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var schema = RuleBuilder.create();
        var table = RuleChecks.table(schema, 1, true, false);
        var b = schema.transform(CalciteRuntime::configure);
        var left = b.scan(table).build();
        b.scan(table).scan(table);
        var rex = b.getRexBuilder();
        var condition = RuleChecks.eq(b,
                rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 1));
        b.join(JoinRelType.LEFT, condition);
        var joined = b.build();
        RelNode input = LogicalProject.create(joined, List.of(), List.of(rex.makeInputRef(joined, 0)), (List<String>) null);
        RelNode expected = b.scan(table).project(rex.makeInputRef(left, 0)).build();
        RuleChecks.rewrites(rule, input, expected);

        var nonUniqueSchema = RuleBuilder.create();
        var nonUniqueTable = RuleChecks.table(nonUniqueSchema, 1, false, false);
        var n = nonUniqueSchema.transform(CalciteRuntime::configure);
        var nonUniqueLeft = n.scan(nonUniqueTable).build();
        n.scan(nonUniqueTable).scan(nonUniqueTable);
        var nonUniqueCondition = RuleChecks.eq(n,
                n.getRexBuilder().makeInputRef(nonUniqueLeft.getRowType().getFieldList().get(0).getType(), 0),
                n.getRexBuilder().makeInputRef(nonUniqueLeft.getRowType().getFieldList().get(0).getType(), 1));
        n.join(JoinRelType.LEFT, nonUniqueCondition);
        var nonUniqueJoin = n.build();
        RuleChecks.doesNotApply(rule, LogicalProject.create(nonUniqueJoin, List.of(),
                List.of(n.getRexBuilder().makeInputRef(nonUniqueJoin, 0)), (List<String>) null));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderProjectLeft test...");
        runTest();
    }
}
