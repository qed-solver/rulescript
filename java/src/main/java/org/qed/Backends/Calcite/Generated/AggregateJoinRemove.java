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

public class AggregateJoinRemove extends RelRule<AggregateJoinRemove.Config> {
	protected AggregateJoinRemove(Config config) {
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
		if (!(r_0.getGroupSet().cardinality() == 1)) {
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
		final LogicalJoin r_1 = call.rel(1);
		if (!(r_1.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_2 = r_1.getCondition();
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "L_Type", true, r_2, 0))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "R_Type", true, r_3, 0))) {
			return;
		}
		b.push(r_2);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateJoinRemove toRule() {
			return new AggregateJoinRemove(this);
		}

		@Override
		default String description() {
			return "AggregateJoinRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).inputs(s_2 -> s_2.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(RelNode.class).anyInputs()));
		}
	}
}
