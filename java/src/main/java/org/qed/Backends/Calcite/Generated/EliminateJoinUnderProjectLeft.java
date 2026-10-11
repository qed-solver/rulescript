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

public class EliminateJoinUnderProjectLeft extends RelRule<EliminateJoinUnderProjectLeft.Config> {
	protected EliminateJoinUnderProjectLeft(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		if (!(r_0.getProjects().size() == 1)) {
			return;
		}
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 0)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		if (!(r_1.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_1 = r_1.getCondition();
		if (!(e_1 instanceof RexCall && ((RexCall) e_1).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_1).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_2 = ((RexCall) e_1).getOperands().get(0);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 0)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_1).getOperands().get(1);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 1)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", false, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.isUnique(call, r_2, 0))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.deepEquals(r_2))) {
			return;
		}
		b.push(r_2);
		b.project(CalciteRuntime.field(b, 1, 0));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default EliminateJoinUnderProjectLeft toRule() {
			return new EliminateJoinUnderProjectLeft(this);
		}

		@Override
		default String description() {
			return "EliminateJoinUnderProjectLeft";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).inputs(s_2 -> s_2.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(RelNode.class).anyInputs()));
		}
	}
}
