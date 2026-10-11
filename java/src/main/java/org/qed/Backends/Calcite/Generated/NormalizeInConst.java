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

public class NormalizeInConst extends RelRule<NormalizeInConst.Config> {
	protected NormalizeInConst(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> d_1 = RelOptUtil.disjunctions(e_0);
		if (!(d_1.size() == 4)) {
			return;
		}
		final RexNode e_2 = d_1.get(0);
		if (!(e_2 instanceof RexCall && ((RexCall) e_2).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_2).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_2).getOperands().get(0);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_2).getOperands().get(1);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 1)) {
			return;
		}
		final RexNode e_5 = d_1.get(1);
		if (!(e_5 instanceof RexCall && ((RexCall) e_5).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_5).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_6 = ((RexCall) e_5).getOperands().get(0);
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 0)) {
			return;
		}
		final RexNode e_7 = ((RexCall) e_5).getOperands().get(1);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 2)) {
			return;
		}
		final RexNode e_8 = d_1.get(2);
		if (!(e_8 instanceof RexCall && ((RexCall) e_8).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_8).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_9 = ((RexCall) e_8).getOperands().get(0);
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 0)) {
			return;
		}
		final RexNode e_10 = ((RexCall) e_8).getOperands().get(1);
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 1)) {
			return;
		}
		final RexNode e_11 = d_1.get(3);
		if (!(e_11 instanceof RexCall && ((RexCall) e_11).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_11).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_12 = ((RexCall) e_11).getOperands().get(0);
		if (!(e_12 instanceof RexInputRef && ((RexInputRef) e_12).getIndex() == 0)) {
			return;
		}
		final RexNode e_13 = ((RexCall) e_11).getOperands().get(1);
		if (!(e_13 instanceof RexInputRef && ((RexInputRef) e_13).getIndex() == 2)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 3)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1, 2))) {
			return;
		}
		b.push(r_1);
		b.filter(b.or(b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)), b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 2))));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default NormalizeInConst toRule() {
			return new NormalizeInConst(this);
		}

		@Override
		default String description() {
			return "NormalizeInConst";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
