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

public class CalcMerge extends RelRule<CalcMerge.Config> {
	protected CalcMerge(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		if (!(r_0.getProjects().size() == 2)) {
			return;
		}
		final RexNode e_0 = r_0.getProjects().get(0);
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final RexNode e_3 = r_0.getProjects().get(1);
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final LogicalFilter r_1 = call.rel(1);
		final RexNode e_6 = r_1.getCondition();
		final RexNode[] args_7 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_8 = e_6;
		if (!(CalciteRuntime.abstracts(sym_8, args_7))) {
			return;
		}
		final LogicalProject r_2 = call.rel(2);
		if (!(r_2.getProjects().size() == 2)) {
			return;
		}
		final RexNode e_9 = r_2.getProjects().get(0);
		final RexNode[] args_10 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_11 = e_9;
		if (!(CalciteRuntime.abstracts(sym_11, args_10))) {
			return;
		}
		final RexNode e_12 = r_2.getProjects().get(1);
		final RexNode[] args_13 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_14 = e_12;
		if (!(CalciteRuntime.abstracts(sym_14, args_13))) {
			return;
		}
		final LogicalFilter r_3 = call.rel(3);
		final RexNode e_15 = r_3.getCondition();
		final RexNode[] args_16 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_17 = e_15;
		if (!(CalciteRuntime.abstracts(sym_17, args_16))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 2)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "X_Type", true, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Y_Type", true, r_4, 1))) {
			return;
		}
		b.push(r_4);
		b.filter(b.and(CalciteRuntime.instantiate(sym_17, args_16, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.instantiate(sym_8, args_7, new RexNode[]{CalciteRuntime.instantiate(sym_11, args_10, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)})})));
		b.project(List.<RexNode>of(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.instantiate(sym_11, args_10, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)})}), CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.instantiate(sym_11, args_10, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)})})));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default CalcMerge toRule() {
			return new CalcMerge(this);
		}

		@Override
		default String description() {
			return "CalcMerge";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_2 -> s_2.operand(LogicalProject.class).oneInput(s_3 -> s_3.operand(LogicalFilter.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs()))));
		}
	}
}
