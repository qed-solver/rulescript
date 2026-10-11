package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteRuntime;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.rel.logical.LogicalMinus;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.stream.IntStream;

public final class FaithfulPlans {
    private FaithfulPlans() {}

    public record Options(boolean exact, boolean concrete) {}

    public static RelNode of(RelRN pattern) {
        return build(pattern, new Options(true, false));
    }

    public static RelNode concrete(RelRN pattern) {
        return build(pattern, new Options(true, true));
    }

    public static RelNode expected(RelRN pattern) {
        return build(pattern, new Options(false, false));
    }

    public static RelNode expectedConcrete(RelRN pattern) {
        return build(pattern, new Options(false, true));
    }

    private static RelNode build(RelRN pattern, Options options) {
        var builder = RuleBuilder.create().transform(config -> CalciteRuntime.configure(config)
                .withSimplifyValues(false).withAggregateUnique(false).withDedupAggregateCalls(false)
                .withRemoveRedundantDistinct(false));
        push(builder, pattern, options);
        return builder.build();
    }

    private static void push(RelBuilder builder, RelRN pattern, Options options) {
        switch (pattern) {
            case RelRN.Filter filter -> {
                push(builder, filter.source(), options);
                if (options.exact()) {
                    builder.push(LogicalFilter.create(builder.build(), rex(builder, options, filter.cond())));
                } else {
                    builder.filter(rex(builder, options, filter.cond()));
                }
            }
            case RelRN.Project project -> {
                push(builder, project.source(), options);
                builder.project(List.of(rex(builder, options, project.map())), List.of(), options.exact());
            }
            case RelRN.ProjectMany project -> {
                push(builder, project.source(), options);
                builder.project(project.exprs().map(e -> rex(builder, options, e)).asJava(), List.of(), options.exact());
            }
            case RelRN.Join join ->
                    pushJoin(builder, join.ty().semantics(), join.cond(), join.left(), join.right(), options);
            case RelRN.JoinWithSeparateConds join ->
                    pushJoin(builder, join.ty().semantics(), join.cond(), join.left(), join.right(), options);
            case RelRN.JoinWithPushedConds join ->
                    pushJoin(builder, join.ty().semantics(), join.cond(), join.left(), join.right(), options);
            case RelRN.Union union -> pushSetOp(builder, union.sources().asJava(), options, union.all(), "union");
            case RelRN.Intersect intersect ->
                    pushSetOp(builder, intersect.sources().asJava(), options, intersect.all(), "intersect");
            case RelRN.Minus minus -> pushSetOp(builder, minus.sources().asJava(), options, minus.all(), "minus");
            case RelRN.Aggregate aggregate -> pushAggregate(builder, aggregate, options);
            case RelRN.Correlate correlate -> pushCorrelate(builder, correlate, options);
            default -> builder.push(pattern.semantics());
        }
    }

    private static void pushCorrelate(RelBuilder builder, RelRN.Correlate correlate, Options options) {
        push(builder, correlate.left(), options);
        push(builder, correlate.right(), options);
        var right = builder.build();
        var left = builder.build();
        var rexBuilder = builder.getRexBuilder();
        var id = builder.getCluster().createCorrel();
        var corrVar = rexBuilder.makeCorrel(left.getRowType(), id);
        var arguments = new java.util.ArrayList<RexNode>();
        for (var i = 0; i < left.getRowType().getFieldCount(); i++) {
            arguments.add(rexBuilder.makeFieldAccess(corrVar, i));
        }
        var rightFields = right.getRowType().getFieldList();
        for (var j = 0; j < rightFields.size(); j++) {
            arguments.add(rexBuilder.makeInputRef(rightFields.get(j).getType(), j));
        }
        var condition = options.concrete() && correlate.cond() instanceof RuleBuilder.QedFunction
                ? concretePredicate(rexBuilder, correlate.cond().getName(), arguments)
                : rexBuilder.makeCall(correlate.cond(), arguments);
        builder.push(org.apache.calcite.rel.logical.LogicalCorrelate.create(left,
                LogicalFilter.create(right, condition), List.of(), id,
                org.apache.calcite.util.ImmutableBitSet.range(left.getRowType().getFieldCount()), correlate.ty()));
    }

    private static void pushJoin(RelBuilder builder, JoinRelType type, RexRN cond, RelRN left, RelRN right,
                                 Options options) {
        push(builder, left, options);
        push(builder, right, options);
        builder.join(type, rex(builder, options, cond));
    }

    private static void pushSetOp(RelBuilder builder, List<RelRN> sources, Options options, boolean all, String kind) {
        if (!options.exact()) {
            sources.forEach(source -> push(builder, source, options));
            switch (kind) {
                case "union" -> builder.union(all, sources.size());
                case "intersect" -> builder.intersect(all, sources.size());
                default -> builder.minus(all, sources.size());
            }
            return;
        }
        var inputs = sources.stream().map(source -> {
            push(builder, source, options);
            return builder.build();
        }).toList();
        builder.push(switch (kind) {
            case "union" -> LogicalUnion.create(inputs, all);
            case "intersect" -> LogicalIntersect.create(inputs, all);
            default -> LogicalMinus.create(inputs, all);
        });
    }

    private static void pushAggregate(RelBuilder builder, RelRN.Aggregate aggregate, Options options) {
        push(builder, aggregate.source(), options);
        var plain = aggregate.groupSet().allMatch(e -> rex(builder, options, e) instanceof org.apache.calcite.rex.RexInputRef)
                && aggregate.aggCalls().allMatch(c -> c.operands()
                .allMatch(e -> rex(builder, options, e) instanceof org.apache.calcite.rex.RexInputRef));
        if (!plain || !options.exact()) {
            var key = builder.groupKey(aggregate.groupSet().map(e -> rex(builder, options, e)).asJava());
            var calls = aggregate.aggCalls().map(call -> builder.aggregateCall(function(call), call.distinct(), null,
                    call.name(), call.operands().map(e -> rex(builder, options, e)).asJava()));
            builder.aggregate(key, calls);
            return;
        }
        var input = builder.build();
        var groups = aggregate.groupSet().map(e -> ((org.apache.calcite.rex.RexInputRef) rex(builder, options, e)).getIndex());
        var groupSet = ImmutableBitSet.of(groups.asJava());
        var calls = aggregate.aggCalls().map(call -> AggregateCall.create(function(call), call.distinct(), false,
                false, call.operands().map(e -> ((org.apache.calcite.rex.RexInputRef) rex(builder, options, e)).getIndex())
                        .asJava(), -1, null, RelCollations.EMPTY, groupSet.cardinality(), input, null, call.name()));
        builder.push(LogicalAggregate.create(input, List.of(), groupSet, null, calls.asJava()));
    }

    private static org.apache.calcite.sql.SqlAggFunction function(RelRN.AggCall call) {
        return call.operator() != null ? call.operator()
                : RuleBuilder.create().genericAggregateOp(call.name(), call.type());
    }

    private static RexNode rex(RelBuilder builder, Options options, RexRN pattern) {
        var rexBuilder = builder.getRexBuilder();
        return switch (pattern) {
            case RexRN.And and -> options.exact()
                    ? rexBuilder.makeCall(SqlStdOperatorTable.AND, and.sources().map(e -> rex(builder, options, e)).asJava())
                    : builder.and(and.sources().map(e -> rex(builder, options, e)).asJava());
            case RexRN.Or or -> options.exact()
                    ? rexBuilder.makeCall(SqlStdOperatorTable.OR, or.sources().map(e -> rex(builder, options, e)).asJava())
                    : builder.or(or.sources().map(e -> rex(builder, options, e)).asJava());
            case RexRN.Not not -> options.exact()
                    ? rexBuilder.makeCall(SqlStdOperatorTable.NOT, rex(builder, options, not.source()))
                    : builder.not(rex(builder, options, not.source()));
            case RexRN.Pred pred -> {
                var arguments = pred.sources().map(e -> rex(builder, options, e)).asJava();
                yield options.concrete() && !arguments.isEmpty() && pred.operator() instanceof RuleBuilder.QedFunction
                        ? concretePredicate(rexBuilder, pred.operator().getName(), arguments)
                        : rexBuilder.makeCall(pred.operator(), arguments);
            }
            case RexRN.Proj proj ->
                    rexBuilder.makeCall(proj.operator(), proj.sources().map(e -> rex(builder, options, e)).asJava());
            case RexRN.GroupBy groupBy ->
                    rexBuilder.makeCall(groupBy.operator(), groupBy.sources().map(e -> rex(builder, options, e)).asJava());
            default -> {
                var body = subqueryBody(pattern);
                yield body == null ? pattern.semantics()
                        : org.apache.calcite.rex.RexSubQuery.exists(build(body, options));
            }
        };
    }

    private static RelRN subqueryBody(RexRN pattern) {
        if (!pattern.getClass().isRecord() || pattern.getClass().getEnclosingClass() == RexRN.class) {
            return null;
        }
        try {
            for (var component : pattern.getClass().getRecordComponents()) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                if (accessor.invoke(pattern) instanceof RelRN body) {
                    return body;
                }
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return null;
    }

    private static final List<org.apache.calcite.sql.SqlOperator> UNARY_PREDICATES = List.of(
            SqlStdOperatorTable.IS_NOT_NULL, SqlStdOperatorTable.IS_NULL, SqlStdOperatorTable.IS_NOT_FALSE);

    private static final List<org.apache.calcite.sql.SqlOperator> BINARY_PREDICATES = List.of(
            SqlStdOperatorTable.EQUALS, SqlStdOperatorTable.NOT_EQUALS, SqlStdOperatorTable.GREATER_THAN,
            SqlStdOperatorTable.LESS_THAN, SqlStdOperatorTable.GREATER_THAN_OR_EQUAL,
            SqlStdOperatorTable.LESS_THAN_OR_EQUAL);

    private static RexNode concretePredicate(org.apache.calcite.rex.RexBuilder rexBuilder, String name,
                                             List<RexNode> arguments) {
        var pick = Math.abs(name.hashCode());
        return switch (arguments.size()) {
            case 1 -> rexBuilder.makeCall(UNARY_PREDICATES.get(pick % UNARY_PREDICATES.size()), arguments);
            case 2 -> rexBuilder.makeCall(BINARY_PREDICATES.get(pick % BINARY_PREDICATES.size()), arguments);
            default -> rexBuilder.makeCall(SqlStdOperatorTable.NOT, rexBuilder.makeCall(SqlStdOperatorTable.AND,
                    arguments.stream().map(argument -> rexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, argument))
                            .toList()));
        };
    }
}
