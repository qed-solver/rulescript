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

public class EliminateAggDistinctForKeys extends RelRule<EliminateAggDistinctForKeys.Config> {
	protected EliminateAggDistinctForKeys(Config config) {
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
		if (!(r_0.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final RexNode e_1 = CalciteRuntime.ref(g_0.get(0));
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_2 = r_0.getAggCallList().get(0);
		if (!(!a_2.hasFilter() && a_2.getCollation().getFieldCollations().isEmpty() && a_2.isDistinct() == true && a_2.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_3 = CalciteRuntime.ref(a_2.getArgList().get(0));
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "S_Type", false, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.isUnique(call, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_2.getAggregation(), false, null, a_2.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 0))));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default EliminateAggDistinctForKeys toRule() {
			return new EliminateAggDistinctForKeys(this);
		}

		@Override
		default String description() {
			return "EliminateAggDistinctForKeys";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
