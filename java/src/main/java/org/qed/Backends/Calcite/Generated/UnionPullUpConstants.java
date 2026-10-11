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

public class UnionPullUpConstants extends RelRule<UnionPullUpConstants.Config> {
	protected UnionPullUpConstants(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalUnion r_0 = call.rel(0);
		if (!(r_0.all == true)) {
			return;
		}
		if (!(r_0.getInputs().size() == 2)) {
			return;
		}
		final LogicalProject r_1 = call.rel(1);
		if (!(r_1.getProjects().size() == 2)) {
			return;
		}
		final RexNode e_0 = r_1.getProjects().get(0);
		if (!(e_0.isAlwaysTrue())) {
			return;
		}
		final RexNode e_1 = r_1.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 1)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 2)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_2, 1))) {
			return;
		}
		final LogicalProject r_3 = call.rel(3);
		if (!(r_3.getProjects().size() == 2)) {
			return;
		}
		final RexNode e_2 = r_3.getProjects().get(0);
		if (!(e_2.isAlwaysTrue())) {
			return;
		}
		final RexNode e_3 = r_3.getProjects().get(1);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 1)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 2)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_4, 1))) {
			return;
		}
		b.push(r_2);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 1)));
		b.push(r_4);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 1)));
		b.union(true, 2);
		b.project(List.<RexNode>of(b.literal(true), CalciteRuntime.field(b, 1, 0)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default UnionPullUpConstants toRule() {
			return new UnionPullUpConstants(this);
		}

		@Override
		default String description() {
			return "UnionPullUpConstants";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalUnion.class).inputs(s_1 -> s_1.operand(LogicalProject.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()), s_3 -> s_3.operand(LogicalProject.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs()));
		}
	}
}
