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

public class MultiJoinOptimizeBushy extends RelRule<MultiJoinOptimizeBushy.Config> {
	protected MultiJoinOptimizeBushy(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		final RexNode e_3 = r_1.getCondition();
		if (!(e_3.isAlwaysTrue())) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		final RexNode e_4 = r_2.getCondition();
		if (!(e_4.isAlwaysTrue())) {
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
		final RelNode r_5 = call.rel(5);
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_5, 0))) {
			return;
		}
		final RelNode r_6 = call.rel(6);
		if (!(CalciteRuntime.bindColumn(types, "D_Type", true, r_6, 0))) {
			return;
		}
		b.push(r_3);
		b.push(r_4);
		b.join(JoinRelType.INNER, b.literal(true));
		b.push(r_5);
		b.push(r_6);
		b.join(JoinRelType.INNER, b.literal(true));
		b.join(JoinRelType.INNER, CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1), CalciteRuntime.field(b, 2, 2), CalciteRuntime.field(b, 2, 3)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default MultiJoinOptimizeBushy toRule() {
			return new MultiJoinOptimizeBushy(this);
		}

		@Override
		default String description() {
			return "MultiJoinOptimizeBushy";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).predicate(r_0 -> r_0.getJoinType() == JoinRelType.INNER).inputs(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(LogicalJoin.class).predicate(r_2 -> r_2.getJoinType() == JoinRelType.INNER).inputs(s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 1).anyInputs(), s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 1).anyInputs()), s_5 -> s_5.operand(RelNode.class).predicate(r_5 -> r_5.getRowType().getFieldCount() == 1).anyInputs()), s_6 -> s_6.operand(RelNode.class).predicate(r_6 -> r_6.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
