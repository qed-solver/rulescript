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

public class IntersectToExists extends RelRule<IntersectToExists.Config> {
	protected IntersectToExists(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalIntersect r_0 = call.rel(0);
		if (!(r_0.all == false)) {
			return;
		}
		if (!(r_0.getInputs().size() == 2)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_1, 0))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_2, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_2);
		CalciteRuntime.correlate(b, JoinRelType.SEMI, ca_0 -> b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, ca_0));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default IntersectToExists toRule() {
			return new IntersectToExists(this);
		}

		@Override
		default String description() {
			return "IntersectToExists";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalIntersect.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(RelNode.class).anyInputs());
		}
	}
}
