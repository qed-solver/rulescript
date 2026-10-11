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

public class AggregateJoinJoinRemove extends RelRule<AggregateJoinJoinRemove.Config> {
	protected AggregateJoinJoinRemove(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalAggregate r_0 = call.rel(0);
		if (!(r_0.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_0.getGroupSet().cardinality() == 2)) {
			return;
		}
		if (!(r_0.getAggCallList().size() == 0)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final RexNode e_1 = CalciteRuntime.ref(g_0.get(0));
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final RexNode e_2 = CalciteRuntime.ref(g_0.get(1));
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 2)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		if (!(r_1.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_3 = r_1.getCondition();
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(2)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		if (!(r_2.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_6 = r_2.getCondition();
		final RexNode[] args_7 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_8 = e_6;
		if (!(CalciteRuntime.abstracts(sym_8, args_7))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_4, 0))) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(r_5.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_5, 0))) {
			return;
		}
		b.push(r_3);
		b.push(r_5);
		b.join(JoinRelType.LEFT, CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)}));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateJoinJoinRemove toRule() {
			return new AggregateJoinJoinRemove(this);
		}

		@Override
		default String description() {
			return "AggregateJoinJoinRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).inputs(s_2 -> s_2.operand(LogicalJoin.class).inputs(s_3 -> s_3.operand(RelNode.class).anyInputs(), s_4 -> s_4.operand(RelNode.class).anyInputs()), s_5 -> s_5.operand(RelNode.class).anyInputs()));
		}
	}
}
