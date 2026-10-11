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

public class UnionMerge extends RelRule<UnionMerge.Config> {
	protected UnionMerge(Config config) {
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
		if (!(r_0.getInputs().size() == 3)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_1, 0))) {
			return;
		}
		final LogicalUnion r_2 = call.rel(2);
		if (!(r_2.all == true)) {
			return;
		}
		if (!(r_2.getInputs().size() == 2)) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_4, 0))) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(r_5.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_5, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_3);
		b.push(r_4);
		b.push(r_5);
		b.union(true, 4);
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default UnionMerge toRule() {
			return new UnionMerge(this);
		}

		@Override
		default String description() {
			return "UnionMerge";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalUnion.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalUnion.class).inputs(s_3 -> s_3.operand(RelNode.class).anyInputs(), s_4 -> s_4.operand(RelNode.class).anyInputs()), s_5 -> s_5.operand(RelNode.class).anyInputs());
		}
	}
}
