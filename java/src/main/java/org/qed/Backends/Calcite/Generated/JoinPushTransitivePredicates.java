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

public class JoinPushTransitivePredicates extends RelRule<JoinPushTransitivePredicates.Config> {
	protected JoinPushTransitivePredicates(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		if (!(r_0.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexCall && ((RexCall) e_0).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_0).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_1 = ((RexCall) e_0).getOperands().get(0);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final RexNode e_2 = ((RexCall) e_0).getOperands().get(1);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Key_Type", false, r_1, 0))) {
			return;
		}
		final LogicalFilter r_2 = call.rel(2);
		final RexNode e_3 = r_2.getCondition();
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Key_Type", false, r_3, 0))) {
			return;
		}
		b.push(r_1);
		b.filter(CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.push(r_3);
		b.filter(CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default JoinPushTransitivePredicates toRule() {
			return new JoinPushTransitivePredicates(this);
		}

		@Override
		default String description() {
			return "JoinPushTransitivePredicates";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalFilter.class).oneInput(s_3 -> s_3.operand(RelNode.class).anyInputs()));
		}
	}
}
