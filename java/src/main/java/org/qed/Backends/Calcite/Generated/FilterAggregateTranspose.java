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

public class FilterAggregateTranspose extends RelRule<FilterAggregateTranspose.Config> {
	protected FilterAggregateTranspose(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = CalciteRuntime.conjunctions(e_0);
		if (!(c_1.size() == 3)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RexNode e_5 = c_1.get(1);
		final RexNode[] args_6 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_7 = e_5;
		if (!(CalciteRuntime.abstracts(sym_7, args_6))) {
			return;
		}
		final RexNode e_8 = c_1.get(2);
		final RexNode[] args_9 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_10 = e_8;
		if (!(CalciteRuntime.abstracts(sym_10, args_9))) {
			return;
		}
		final LogicalAggregate r_1 = call.rel(1);
		if (!(r_1.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_1.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_1.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_11 = r_1.getGroupSet().asList();
		final RexNode e_12 = CalciteRuntime.ref(g_11.get(0));
		if (!(e_12 instanceof RexInputRef && ((RexInputRef) e_12).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_13 = r_1.getAggCallList().get(0);
		if (!(!a_13.hasFilter() && a_13.getCollation().getFieldCollations().isEmpty() && a_13.isDistinct() == false && a_13.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_14 = CalciteRuntime.ref(a_13.getArgList().get(0));
		if (!(e_14 instanceof RexInputRef && ((RexInputRef) e_14).getIndex() == 1)) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		final RexNode e_15 = r_2.getCondition();
		if (!(e_15.isAlwaysTrue())) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_4, 0))) {
			return;
		}
		b.push(r_3);
		b.push(r_4);
		b.join(JoinRelType.INNER, b.literal(true));
		b.filter(CalciteRuntime.instantiate(sym_4, args_3, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_13.getAggregation(), false, null, a_13.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		b.filter(b.and(CalciteRuntime.instantiate(sym_7, args_6, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.instantiate(sym_10, args_9, new RexNode[]{CalciteRuntime.field(b, 1, 1)})));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default FilterAggregateTranspose toRule() {
			return new FilterAggregateTranspose(this);
		}

		@Override
		default String description() {
			return "FilterAggregateTranspose";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(LogicalAggregate.class).oneInput(s_2 -> s_2.operand(LogicalJoin.class).predicate(r_2 -> r_2.getJoinType() == JoinRelType.INNER).inputs(s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 1).anyInputs(), s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 1).anyInputs())));
		}
	}
}
