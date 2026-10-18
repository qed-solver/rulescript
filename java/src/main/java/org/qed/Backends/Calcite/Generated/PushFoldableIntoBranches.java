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

public class PushFoldableIntoBranches extends RelRule<PushFoldableIntoBranches.Config> {
	protected PushFoldableIntoBranches(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexCall && ((RexCall) e_0).getOperands().size() == 1)) {
			return;
		}
		final SqlOperator op_1 = ((RexCall) e_0).getOperator();
		final RexNode e_2 = ((RexCall) e_0).getOperands().get(0);
		if (!(e_2 instanceof RexCall && ((RexCall) e_2).getOperands().size() == 3)) {
			return;
		}
		final SqlOperator op_3 = ((RexCall) e_2).getOperator();
		final RexNode e_4 = ((RexCall) e_2).getOperands().get(0);
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final RexNode e_7 = ((RexCall) e_2).getOperands().get(1);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 0)) {
			return;
		}
		final RexNode e_8 = ((RexCall) e_2).getOperands().get(2);
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "VAL", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "VAL", true, r_1, 1))) {
			return;
		}
		b.push(r_1);
		b.project(b.call(op_3, CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), b.call(op_1, CalciteRuntime.field(b, 1, 0)), b.call(op_1, CalciteRuntime.field(b, 1, 1))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PushFoldableIntoBranches toRule() {
			return new PushFoldableIntoBranches(this);
		}

		@Override
		default String description() {
			return "PushFoldableIntoBranches";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 1).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2).anyInputs());
		}
	}
}
