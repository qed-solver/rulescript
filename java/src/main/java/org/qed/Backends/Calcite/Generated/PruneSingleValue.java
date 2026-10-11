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

public class PruneSingleValue extends RelRule<PruneSingleValue.Config> {
	protected PruneSingleValue(Config config) {
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
		if (!(CalciteRuntime.bindColumn(types, "O_Type", true, r_1, 0))) {
			return;
		}
		final LogicalValues r_2 = call.rel(2);
		if (!(CalciteRuntime.sameValues(call, r_2, new String[]{"v"}, true))) {
			return;
		}
		b.push(r_1);
		b.filter(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 0), b.literal(true)}));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), b.literal(true)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneSingleValue toRule() {
			return new PruneSingleValue(this);
		}

		@Override
		default String description() {
			return "PruneSingleValue";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalValues.class).noInputs());
		}
	}
}
