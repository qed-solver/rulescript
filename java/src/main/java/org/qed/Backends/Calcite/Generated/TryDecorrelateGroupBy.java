package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.*;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.EmptyConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TryDecorrelateGroupBy extends RelRule<TryDecorrelateGroupBy.Config> {
	protected TryDecorrelateGroupBy(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		final RexNode e_3 = r_1.getCondition();
		if (!(e_3.isAlwaysTrue())) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "L_Type", false, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.isUnique(call, r_2, 0))) {
			return;
		}
		final LogicalAggregate r_3 = call.rel(3);
		if (!(r_3.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_3.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_3.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_4 = r_3.getGroupSet().asList();
		final RexNode e_5 = CalciteRuntime.ref(g_4.get(0));
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_6 = r_3.getAggCallList().get(0);
		if (!(!a_6.hasFilter() && a_6.getCollation().getFieldCollations().isEmpty() && a_6.isDistinct() == false && a_6.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_7 = CalciteRuntime.ref(a_6.getArgList().get(0));
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 1)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(CalciteRuntime.bindColumn(types, "G_Type", false, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "A_Type", false, r_4, 1))) {
			return;
		}
		b.push(r_2);
		b.push(r_4);
		b.join(JoinRelType.INNER, b.literal(true));
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)), b.aggregateCall((SqlAggFunction) a_6.getAggregation(), false, null, a_6.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		b.filter(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default TryDecorrelateGroupBy toRule() {
			return new TryDecorrelateGroupBy(this);
		}

		@Override
		default String description() {
			return "TryDecorrelateGroupBy";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1 && !r_2.getRowType().getFieldList().get(0).getType().isNullable()).anyInputs(), s_3 -> s_3.operand(LogicalAggregate.class).oneInput(s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 2 && !r_4.getRowType().getFieldList().get(0).getType().isNullable() && !r_4.getRowType().getFieldList().get(1).getType().isNullable()).anyInputs())));
		}
	}
}
