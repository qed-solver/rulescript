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

public class TryRemapJoinOuterColsLeft extends RelRule<TryRemapJoinOuterColsLeft.Config> {
	protected TryRemapJoinOuterColsLeft(Config config) {
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
		final LogicalCorrelate r_1 = call.rel(1);
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_2, 0))) {
			return;
		}
		final LogicalFilter r_3 = call.rel(3);
		if (!(r_1.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode[] ca_3 = CalciteRuntime.correlateArguments(r_1);
		if (!(CalciteRuntime.isCall(r_3.getCondition(), SqlStdOperatorTable.EQUALS, ca_3))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_4, 0))) {
			return;
		}
		b.push(r_2);
		b.push(r_4);
		CalciteRuntime.correlate(b, JoinRelType.INNER, ca_4 -> b.call(SqlStdOperatorTable.EQUALS, ca_4));
		b.filter(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 1)}));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default TryRemapJoinOuterColsLeft toRule() {
			return new TryRemapJoinOuterColsLeft(this);
		}

		@Override
		default String description() {
			return "TryRemapJoinOuterColsLeft";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(LogicalCorrelate.class).inputs(s_2 -> s_2.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(LogicalFilter.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs())));
		}
	}
}
