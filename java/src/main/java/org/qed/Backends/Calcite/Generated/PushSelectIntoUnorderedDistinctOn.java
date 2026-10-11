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

public class PushSelectIntoUnorderedDistinctOn extends RelRule<PushSelectIntoUnorderedDistinctOn.Config> {
	protected PushSelectIntoUnorderedDistinctOn(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = RelOptUtil.conjunctions(e_0);
		if (!(c_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RexNode e_5 = c_1.get(1);
		final RexNode[] args_6 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_7 = e_5;
		if (!(CalciteRuntime.abstracts(sym_7, args_6))) {
			return;
		}
		final LogicalAggregate r_1 = call.rel(1);
		if (!(r_1.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_1.getGroupSet().cardinality() == 2)) {
			return;
		}
		if (!(r_1.getAggCallList().size() == 0)) {
			return;
		}
		final List<Integer> g_8 = r_1.getGroupSet().asList();
		final RexNode e_9 = CalciteRuntime.ref(g_8.get(0));
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 0)) {
			return;
		}
		final RexNode e_10 = CalciteRuntime.ref(g_8.get(1));
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 1)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 2)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "A_Type", false, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "B_Type", false, r_2, 1))) {
			return;
		}
		b.push(r_2);
		b.filter(b.and(CalciteRuntime.instantiate(sym_4, args_3, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.instantiate(sym_7, args_6, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)})));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PushSelectIntoUnorderedDistinctOn toRule() {
			return new PushSelectIntoUnorderedDistinctOn(this);
		}

		@Override
		default String description() {
			return "PushSelectIntoUnorderedDistinctOn";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(LogicalAggregate.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()));
		}
	}
}
