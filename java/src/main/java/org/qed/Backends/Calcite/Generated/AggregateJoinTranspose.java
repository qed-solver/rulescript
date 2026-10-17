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

public class AggregateJoinTranspose extends RelRule<AggregateJoinTranspose.Config> {
	protected AggregateJoinTranspose(Config config) {
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
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 1)) {
			return;
		}
		final RexNode e_2 = CalciteRuntime.ref(g_0.get(1));
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 3)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		final RexNode e_3 = r_1.getCondition();
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(2)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "a_type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "x_type", true, r_2, 1))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(CalciteRuntime.bindColumn(types, "b_type", true, r_3, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "y_type", true, r_3, 1))) {
			return;
		}
		b.push(r_2);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		b.push(r_3);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		b.join(JoinRelType.INNER, CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 2)}));
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 3)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateJoinTranspose toRule() {
			return new AggregateJoinTranspose(this);
		}

		@Override
		default String description() {
			return "AggregateJoinTranspose";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 2).anyInputs(), s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 2).anyInputs()));
		}
	}
}
