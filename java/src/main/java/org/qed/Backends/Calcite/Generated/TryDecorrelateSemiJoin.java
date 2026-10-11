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

public class TryDecorrelateSemiJoin extends RelRule<TryDecorrelateSemiJoin.Config> {
	protected TryDecorrelateSemiJoin(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalCorrelate r_0 = call.rel(0);
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "TL", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.isUnique(call, r_1, 0))) {
			return;
		}
		final LogicalFilter r_2 = call.rel(2);
		if (!(r_0.getJoinType() == JoinRelType.SEMI)) {
			return;
		}
		final RexNode[] ca_0 = CalciteRuntime.correlateArguments(r_0);
		final RexNode sym_1 = r_2.getCondition();
		if (!(CalciteRuntime.abstracts(sym_1, ca_0))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "TR", true, r_3, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_3);
		CalciteRuntime.correlate(b, JoinRelType.INNER, ca_2 -> CalciteRuntime.instantiate(sym_1, ca_0, ca_2));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default TryDecorrelateSemiJoin toRule() {
			return new TryDecorrelateSemiJoin(this);
		}

		@Override
		default String description() {
			return "TryDecorrelateSemiJoin";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalCorrelate.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalFilter.class).oneInput(s_3 -> s_3.operand(RelNode.class).anyInputs()));
		}
	}
}
