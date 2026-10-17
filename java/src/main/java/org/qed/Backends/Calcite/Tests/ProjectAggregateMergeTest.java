package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRule;
import org.qed.RRuleInstances.ProjectAggregateMerge;
import org.qed.RuleBuilder;
import org.qed.RelType;

import java.util.List;

public class ProjectAggregateMergeTest {
    public static void runTest() {
        var rule = new ProjectAggregateMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var scan = builder.scan(table).build();
        var inputField = new RexInputRef(0, scan.getRowType().getFieldList().get(0).getType());
        var groupOp = ruleBuilder.genericProjectionOp("g", new RelType.VarType("g_type", true));
        var groupExpr = builder.getRexBuilder().makeCall(groupOp, List.of(inputField));
        var input = LogicalProject.create(scan, List.of(), List.of(groupExpr, inputField), List.of("g", "arg"));
        var groupSet = ImmutableBitSet.of(0);
        var integer = RelType.fromString("INTEGER", true);
        var f1 = AggregateCall.create(ruleBuilder.genericAggregateOp("f1", integer), false, false, false,
                List.of(1), -1, null, RelCollations.EMPTY, 1, input, null, "f1");
        var f2 = AggregateCall.create(ruleBuilder.genericAggregateOp("f2", integer), false, false, false,
                List.of(1), -1, null, RelCollations.EMPTY, 1, input, null, "f2");
        var f3 = AggregateCall.create(ruleBuilder.genericAggregateOp("f3", integer), false, false, false,
                List.of(1), -1, null, RelCollations.EMPTY, 1, input, null, "f3");
        var beforeAggregate = LogicalAggregate.create(input, List.of(), groupSet, null, List.of(f1, f2, f3));
        var before = select(beforeAggregate, 0, 1, 3);
        var afterInput = LogicalProject.create(scan, List.of(), List.of(inputField, groupExpr), List.of("arg", "g"));
        var afterAggregate = LogicalAggregate.create(afterInput, List.of(), ImmutableBitSet.of(1), null,
                List.of(AggregateCall.create(ruleBuilder.genericAggregateOp("f1", integer), false, false, false,
                                List.of(0), -1, null, RelCollations.EMPTY, 1, afterInput, null, "f1"),
                        AggregateCall.create(ruleBuilder.genericAggregateOp("f3", integer), false, false, false,
                                List.of(0), -1, null, RelCollations.EMPTY, 1, afterInput, null, "f3")));
        var outputBuilder = ruleBuilder.transform(config -> CalciteRuntime.configure(config).withSimplifyValues(false));
        var refs = new java.util.ArrayList<org.apache.calcite.rex.RexNode>();
        for (var i = 0; i < 3; i++) {
            var targetType = before.getRowType().getFieldList().get(i).getType();
            var ref = outputBuilder.getRexBuilder().makeInputRef(afterAggregate, i);
            refs.add(outputBuilder.getRexBuilder().makeCast(targetType, ref));
        }
        var after = LogicalProject.create(afterAggregate, List.of(), refs, List.of("g", "f1", "f3"));
        RuleChecks.rewrites(rule, before, after);

        var retainedAll = select(beforeAggregate, 0, 1, 2, 3);
        RuleChecks.doesNotApply(rule, retainedAll);
    }

    private static RelNode select(RelNode input, int... indexes) {
        var refs = new java.util.ArrayList<org.apache.calcite.rex.RexNode>();
        for (var index : indexes) {
            refs.add(new RexInputRef(index, input.getRowType().getFieldList().get(index).getType()));
        }
        var names = java.util.Arrays.stream(indexes)
                .mapToObj(index -> input.getRowType().getFieldList().get(index).getName()).toList();
        return LogicalProject.create(input, List.of(), refs, names);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectAggregateMerge test...");
        runTest();
    }
}
