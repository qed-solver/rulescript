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

public class InlineJoinConstantsRight extends RelRule<InlineJoinConstantsRight.Config> {
	protected InlineJoinConstantsRight(Config config) {
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
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Left_Type", true, r_1, 0))) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		if (!(r_2.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_3 = r_2.getCondition();
		if (!(e_3 instanceof RexCall && ((RexCall) e_3).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_3).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_3).getOperands().get(0);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 0)) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_3).getOperands().get(1);
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 1)) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Const_Type", false, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.isUnique(call, r_4, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_3);
		b.push(r_4);
		b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)));
		b.join(JoinRelType.INNER, CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 2)}));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default InlineJoinConstantsRight toRule() {
			return new InlineJoinConstantsRight(this);
		}

		@Override
		default String description() {
			return "InlineJoinConstantsRight";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalJoin.class).inputs(s_3 -> s_3.operand(RelNode.class).anyInputs(), s_4 -> s_4.operand(RelNode.class).anyInputs()));
		}
	}
}
