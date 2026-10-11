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

public class RemoveZeroCardLock extends RelRule<RemoveZeroCardLock.Config> {
	protected RemoveZeroCardLock(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final LogicalValues r_1 = call.rel(1);
		if (!(r_1.getTuples().isEmpty())) {
			return;
		}
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		b.values(r_1.getRowType());
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default RemoveZeroCardLock toRule() {
			return new RemoveZeroCardLock(this);
		}

		@Override
		default String description() {
			return "RemoveZeroCardLock";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(LogicalValues.class).noInputs());
		}
	}
}
