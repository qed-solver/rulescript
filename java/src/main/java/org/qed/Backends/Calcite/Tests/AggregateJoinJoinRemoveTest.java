package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateJoinJoinRemove;
import org.qed.RuleBuilder;

public class AggregateJoinJoinRemoveTest {
    public static void runTest() {
        var rule = new AggregateJoinJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        RuleChecks.rewrites(rule, concreteBefore(JoinRelType.LEFT), concreteAfter());
        RuleChecks.doesNotApply(rule, concreteBefore(JoinRelType.INNER));
    }

    private static RelNode concreteBefore(JoinRelType bottomType) {
        var tables = RuleBuilder.create();
        var a = RuleChecks.table(tables, 1, false);
        var b = RuleChecks.table(tables, 1, false);
        var c = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables).transform(config -> CalciteRuntime.configure(config)
                .withSimplifyValues(false).withAggregateUnique(false).withDedupAggregateCalls(false)
                .withRemoveRedundantDistinct(false));
        var left = builder.scan(a).scan(b).join(bottomType,
                builder.call(SqlStdOperatorTable.LESS_THAN, inputRef(builder, builder.peek(1), 0, false),
                        inputRef(builder, builder.peek(0), 1, bottomType == JoinRelType.LEFT))).build();
        return builder.push(left).scan(c).join(JoinRelType.LEFT,
                builder.call(SqlStdOperatorTable.LESS_THAN, inputRef(builder, builder.peek(1), 0, false),
                        inputRef(builder, builder.peek(0), 2, true)))
                .aggregate(builder.groupKey(builder.field(0), builder.field(2))).build();
    }

    private static RelNode concreteAfter() {
        var tables = RuleBuilder.create();
        var a = RuleChecks.table(tables, 1, false);
        RuleChecks.table(tables, 1, false);
        var c = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables).transform(config -> CalciteRuntime.configure(config)
                .withSimplifyValues(false).withAggregateUnique(false).withDedupAggregateCalls(false)
                .withRemoveRedundantDistinct(false));
        return builder.scan(a).scan(c).join(JoinRelType.LEFT,
                builder.call(SqlStdOperatorTable.LESS_THAN, inputRef(builder, builder.peek(1), 0, false),
                        inputRef(builder, builder.peek(0), 1, true)))
                .aggregate(builder.groupKey(builder.field(0), builder.field(1))).build();
    }

    private static org.apache.calcite.rex.RexNode inputRef(org.apache.calcite.tools.RelBuilder builder,
                                                            RelNode input, int index, boolean nullable) {
        var type = input.getRowType().getFieldList().get(0).getType();
        type = builder.getTypeFactory().createTypeWithNullability(type, nullable);
        return builder.getRexBuilder().makeInputRef(type, index);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateJoinJoinRemove test...");
        runTest();
    }
}
