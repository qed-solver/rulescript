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

public class EliminateDistinctSetLeft extends RelRule<EliminateDistinctSetLeft.Config> {
	protected EliminateDistinctSetLeft(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalUnion r_0 = call.rel(0);
		if (!(r_0.all == false)) {
			return;
		}
		if (!(r_0.getInputs().size() == 2)) {
			return;
		}
		final LogicalProject r_1 = call.rel(1);
		if (!(r_1.getProjects().size() == 3)) {
			return;
		}
		final RexNode e_0 = r_1.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 2)) {
			return;
		}
		final RexNode e_1 = r_1.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final RexNode e_2 = r_1.getProjects().get(2);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 1)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 3)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T1_Type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T2_Type", true, r_2, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T3_Type", true, r_2, 2))) {
			return;
		}
		final LogicalValues r_3 = call.rel(3);
		if (!(r_3.getTuples().isEmpty())) {
			return;
		}
		if (!(r_3.getRowType().getFieldCount() == 3)) {
			return;
		}
		b.push(r_2);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default EliminateDistinctSetLeft toRule() {
			return new EliminateDistinctSetLeft(this);
		}

		@Override
		default String description() {
			return "EliminateDistinctSetLeft";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalUnion.class).inputs(s_1 -> s_1.operand(LogicalProject.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()), s_3 -> s_3.operand(LogicalValues.class).noInputs());
		}
	}
}
