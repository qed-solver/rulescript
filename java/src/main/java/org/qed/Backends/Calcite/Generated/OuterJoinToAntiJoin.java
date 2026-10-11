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

public class OuterJoinToAntiJoin extends RelRule<OuterJoinToAntiJoin.Config> {
	protected OuterJoinToAntiJoin(Config config) {
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
		final LogicalFilter r_1 = call.rel(1);
		final RexNode e_1 = r_1.getCondition();
		if (!(e_1 instanceof RexCall && ((RexCall) e_1).getOperator() == SqlStdOperatorTable.IS_NULL && ((RexCall) e_1).getOperands().size() == 1)) {
			return;
		}
		final RexNode e_2 = ((RexCall) e_1).getOperands().get(0);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 1)) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		if (!(r_2.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_3 = r_2.getCondition();
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "L_T", true, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "R_T", false, r_4, 0))) {
			return;
		}
		b.push(r_3);
		b.push(r_4);
		b.join(JoinRelType.ANTI, CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)}));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default OuterJoinToAntiJoin toRule() {
			return new OuterJoinToAntiJoin(this);
		}

		@Override
		default String description() {
			return "OuterJoinToAntiJoin";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_2 -> s_2.operand(LogicalJoin.class).inputs(s_3 -> s_3.operand(RelNode.class).anyInputs(), s_4 -> s_4.operand(RelNode.class).anyInputs())));
		}
	}
}
