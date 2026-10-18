package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.CollapseWindow;

import java.util.List;

public class CollapseWindowTest {
    private static AggregateCall call(org.apache.calcite.rel.RelNode input, String name) {
        var function = name.equals("f1") ? SqlStdOperatorTable.MAX : SqlStdOperatorTable.SUM;
        return AggregateCall.create(function, false, false, false, List.of(1), -1, null,
                RelCollations.EMPTY, 1, input, null, name);
    }

    private static org.apache.calcite.rel.RelNode aggregate(org.apache.calcite.rel.RelNode input, String... names) {
        var calls = java.util.Arrays.stream(names).map(name -> call(input, name)).toList();
        return LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, calls);
    }

    private static RexInputRef ref(org.apache.calcite.rel.RelNode input, int index) {
        return new RexInputRef(index, input.getRowType().getFieldList().get(index).getType());
    }

    private static org.apache.calcite.rel.RelNode join(org.apache.calcite.rel.RelNode left,
            org.apache.calcite.rel.RelNode right, JoinRelType type, int rightKey) {
        var condition = new org.apache.calcite.rex.RexBuilder(left.getCluster().getTypeFactory()).makeCall(
                SqlStdOperatorTable.EQUALS, ref(left, 0),
                new RexInputRef(left.getRowType().getFieldCount() + rightKey,
                        right.getRowType().getFieldList().get(rightKey).getType()));
        return LogicalJoin.create(left, right, List.of(), condition, java.util.Set.of(), type);
    }

    private static org.apache.calcite.rel.RelNode project(org.apache.calcite.rel.RelNode input, int... indexes) {
        var refs = java.util.Arrays.stream(indexes).mapToObj(index -> ref(input, index)).toList();
        var names = java.util.Arrays.stream(indexes).mapToObj(index -> input.getRowType().getFieldNames().get(index)).toList();
        return LogicalProject.create(input, List.of(), refs, names);
    }

    public static void runTest() {
        var rule = new CollapseWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var base = b.scan(table).build();
        var childAgg = aggregate(base, "f1");
        var child = project(join(base, childAgg, JoinRelType.INNER, 0), 0, 1, 3);
        var parentAgg = aggregate(base, "f2");
        var before = project(join(child, parentAgg, JoinRelType.INNER, 0), 0, 1, 2, 4);
        var merged = aggregate(base, "f1", "f2");
        var expected = project(join(base, merged, JoinRelType.INNER, 0), 0, 1, 3, 4);
        RuleChecks.rewrites(rule, before, expected);
        RuleChecks.doesNotApply(rule, join(base, childAgg, JoinRelType.LEFT, 0));
    }

    public static void main(String[] args) {
        System.out.println("Running CollapseWindow test...");
        runTest();
    }
}
