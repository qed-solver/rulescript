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

public class CalcRemove extends RelRule<CalcRemove.Config> {
	protected CalcRemove(Config config) {
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
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 0)) {
			return;
		}
		final RexNode e_1 = r_0.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 2)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Source_Type_0", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Source_Type_1", true, r_1, 1))) {
			return;
		}
		b.push(r_1);
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default CalcRemove toRule() {
			return new CalcRemove(this);
		}

		@Override
		default String description() {
			return "CalcRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
