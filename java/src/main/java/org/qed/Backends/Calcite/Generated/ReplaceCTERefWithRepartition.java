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

public class ReplaceCTERefWithRepartition extends RelRule<ReplaceCTERefWithRepartition.Config> {
	protected ReplaceCTERefWithRepartition(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final RelNode r_0 = call.rel(0);
		if (!(CalciteRuntime.bindColumn(types, "Cte_Type", true, r_0, 0))) {
			return;
		}
		b.push(r_0);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ReplaceCTERefWithRepartition toRule() {
			return new ReplaceCTERefWithRepartition(this);
		}

		@Override
		default String description() {
			return "ReplaceCTERefWithRepartition";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(RelNode.class).predicate(r_0 -> r_0.getRowType().getFieldCount() == 1).anyInputs();
		}
	}
}
