package org.qed.Backends.Calcite;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexFieldAccess;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexShuttle;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rel.logical.LogicalCorrelate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;

import java.util.Map;

public final class CalciteRuntime {
    private CalciteRuntime() {}

    public static RelBuilder.Config configure(RelBuilder.Config config) {
        return config.withSimplify(false).withBloat(-1).withPruneInputOfAggregate(false);
    }

    public static java.util.List<RexNode> conjunctions(RexNode node) {
        return flatten(node, org.apache.calcite.sql.SqlKind.AND);
    }

    public static java.util.List<RexNode> disjunctions(RexNode node) {
        return flatten(node, org.apache.calcite.sql.SqlKind.OR);
    }

    private static java.util.List<RexNode> flatten(RexNode node, org.apache.calcite.sql.SqlKind kind) {
        if (node.getKind() != kind) {
            return java.util.List.of(node);
        }
        final java.util.List<RexNode> out = new java.util.ArrayList<>();
        for (RexNode operand : ((RexCall) node).getOperands()) {
            out.addAll(flatten(operand, kind));
        }
        return out;
    }

    public static void transform(RelOptRuleCall call, RelBuilder b) {
        var result = b.build();
        var original = call.rel(0).getRowType();
        var actual = result.getRowType();
        if (!actual.equals(original) && actual.getFieldCount() == original.getFieldCount()) {
            var sameSansNullability = true;
            for (var i = 0; i < actual.getFieldCount(); i++) {
                sameSansNullability &= SqlTypeUtil.equalSansNullability(b.getTypeFactory(),
                        actual.getFieldList().get(i).getType(), original.getFieldList().get(i).getType());
            }
            if (sameSansNullability) {
                b.push(result);
                var fields = new java.util.ArrayList<RexNode>();
                for (var i = 0; i < original.getFieldCount(); i++) {
                    fields.add(b.getRexBuilder().makeCast(original.getFieldList().get(i).getType(), b.field(i), true));
                }
                result = b.project(fields, original.getFieldNames()).build();
            }
        }
        call.transformTo(result);
    }

    public static RelBuilder.GroupKey groupKey(RelBuilder b, RexNode... keys) {
        var width = b.peek().getRowType().getFieldCount();
        var computed = new java.util.ArrayList<RexNode>();
        var ascending = true;
        var previous = -1;
        for (var key : keys) {
            int ordinal;
            if (key instanceof RexInputRef ref) {
                ordinal = ref.getIndex();
            } else {
                var position = computed.indexOf(key);
                if (position < 0) {
                    computed.add(key);
                    position = computed.size() - 1;
                }
                ordinal = width + position;
            }
            ascending &= ordinal > previous;
            previous = ordinal;
        }
        if (ascending) {
            return b.groupKey(keys);
        }
        var projects = new java.util.ArrayList<RexNode>(b.fields());
        projects.addAll(java.util.Arrays.asList(keys));
        b.project(projects, java.util.List.of(), true);
        var ordinals = new int[keys.length];
        for (var i = 0; i < keys.length; i++) {
            ordinals[i] = width + i;
        }
        return b.groupKey(ordinals);
    }

    public static RelBuilder builder(RelOptRuleCall call) {
        return call.builder().transform(CalciteRuntime::configure);
    }

    public static RexNode field(RelBuilder builder, int inputs, int ordinal) {
        if (inputs == 1) {
            return builder.field(ordinal);
        }
        var remaining = ordinal;
        for (var input = 0; input < inputs; input++) {
            var count = builder.peek(inputs, input).getRowType().getFieldCount();
            if (remaining < count) {
                return builder.field(inputs, input, remaining);
            }
            remaining -= count;
        }
        throw new IllegalArgumentException("Field ordinal out of range: " + ordinal);
    }

    private static final RelDataType ANY =
            new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT).createSqlType(SqlTypeName.ANY);

    public static RexNode ref(int index) {
        return new RexInputRef(index, ANY);
    }

    public static boolean abstracts(RexNode expr, RexNode... arguments) {
        try {
            instantiate(expr, arguments, arguments);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static RexNode instantiate(RexNode expr, RexNode[] arguments, RexNode... replacements) {
        return expr.accept(new RexShuttle() {
            private RexNode replacement(RexNode node) {
                for (var i = 0; i < arguments.length; i++) {
                    if (arguments[i].equals(node)) {
                        return replacements[i];
                    }
                }
                return null;
            }

            @Override
            public RexNode visitCall(RexCall call) {
                var replaced = replacement(call);
                return replaced != null ? replaced : super.visitCall(call);
            }

            @Override
            public RexNode visitFieldAccess(RexFieldAccess access) {
                var replaced = replacement(access);
                return replaced != null ? replaced : super.visitFieldAccess(access);
            }

            @Override
            public RexNode visitCorrelVariable(org.apache.calcite.rex.RexCorrelVariable variable) {
                if (arguments.length == 0) {
                    return variable;
                }
                throw new IllegalStateException("Expression reads correlation variable " + variable
                        + " which is not an argument");
            }

            @Override
            public RexNode visitInputRef(RexInputRef inputRef) {
                var replaced = replacement(inputRef);
                if (replaced == null) {
                    throw new IllegalStateException("Expression reads column " + inputRef.getIndex()
                            + " which is not an argument");
                }
                return replaced;
            }
        });
    }

    public static boolean isCall(RexNode expr, SqlOperator operator, RexNode... arguments) {
        return expr instanceof RexCall call && call.getOperator() == operator
                && call.getOperands().equals(java.util.Arrays.asList(arguments));
    }

    public static RexNode[] correlateArguments(LogicalCorrelate correlate) {
        var rexBuilder = correlate.getCluster().getRexBuilder();
        var leftType = correlate.getLeft().getRowType();
        var corrVar = rexBuilder.makeCorrel(leftType, correlate.getCorrelationId());
        var rightCount = correlate.getRight().getRowType().getFieldCount();
        var arguments = new RexNode[leftType.getFieldCount() + rightCount];
        for (var i = 0; i < leftType.getFieldCount(); i++) {
            arguments[i] = rexBuilder.makeFieldAccess(corrVar, i);
        }
        for (var j = 0; j < rightCount; j++) {
            arguments[leftType.getFieldCount() + j] = ref(j);
        }
        return arguments;
    }

    public static void correlate(RelBuilder builder, JoinRelType type,
                                 java.util.function.Function<RexNode[], RexNode> condition) {
        var right = builder.build();
        var left = builder.build();
        var rexBuilder = builder.getRexBuilder();
        var id = builder.getCluster().createCorrel();
        var corrVar = rexBuilder.makeCorrel(left.getRowType(), id);
        var leftCount = left.getRowType().getFieldCount();
        var arguments = new RexNode[leftCount + right.getRowType().getFieldCount()];
        for (var i = 0; i < leftCount; i++) {
            arguments[i] = rexBuilder.makeFieldAccess(corrVar, i);
        }
        var rightFields = right.getRowType().getFieldList();
        for (var j = 0; j < rightFields.size(); j++) {
            arguments[leftCount + j] = rexBuilder.makeInputRef(rightFields.get(j).getType(), j);
        }
        var filtered = LogicalFilter.create(right, condition.apply(arguments));
        builder.push(LogicalCorrelate.create(left, filtered, java.util.List.of(), id, ImmutableBitSet.range(leftCount),
                type));
    }

    public static boolean sameValues(RelOptRuleCall call, Values values, String[] names, Object... rows) {
        var expected = call.builder().values(names, rows).build();
        return expected instanceof Values v && v.getTuples().equals(values.getTuples());
    }

    public static boolean bindColumn(Map<String, RelDataType> types, String typeId, boolean nullable, RelNode rel,
                                     int ordinal) {
        var actual = rel.getRowType().getFieldList().get(ordinal).getType();
        if (!nullable && actual.isNullable()) {
            return false;
        }
        var previous = types.putIfAbsent(typeId, actual);
        return previous == null || sameType(previous, actual);
    }

    private static boolean sameType(RelDataType left, RelDataType right) {
        if (left instanceof org.qed.RelType.VarType || right instanceof org.qed.RelType.VarType) {
            return left.toString().equals(right.toString());
        }
        return SqlTypeUtil.equalSansNullability(left, right);
    }

    public static boolean hasBoundType(Map<String, RelDataType> types, String typeId, RexNode expr) {
        var bound = types.get(typeId);
        return bound == null || sameType(bound, expr.getType());
    }

    public static RexNode bigint(RelBuilder b, long value) {
        return b.getRexBuilder().makeExactLiteral(java.math.BigDecimal.valueOf(value),
                b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.BIGINT));
    }

    public static boolean isLiteral(RexNode expr, Object value) {
        return expr instanceof org.apache.calcite.rex.RexLiteral literal && !literal.isNull()
                && value.equals(literal.getValueAs(value.getClass()));
    }

    public static boolean isUnique(RelOptRuleCall call, RelNode rel, int ordinal) {
        return Boolean.TRUE.equals(call.getMetadataQuery().areColumnsUnique(rel, ImmutableBitSet.of(ordinal)));
    }
}
