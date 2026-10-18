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

public class RewriteLateralSubquery extends RelRule<RewriteLateralSubquery.Config> {
	protected RewriteLateralSubquery(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalCorrelate r_0 = call.rel(0);
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "l0", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "l1", true, r_1, 1))) {
			return;
		}
		final LogicalFilter r_2 = call.rel(2);
		final RexNode[] ca_0 = CalciteRuntime.correlateArguments(r_0);
		final RexNode sym_1 = r_2.getCondition();
		if (!(CalciteRuntime.abstracts(sym_1, ca_0))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(CalciteRuntime.bindColumn(types, "s0", true, r_3, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "s1", true, r_3, 1))) {
			return;
		}
		b.push(r_1);
		b.push(r_3);
		b.join(JoinRelType.LEFT, CalciteRuntime.instantiate(sym_1, ca_0, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1), CalciteRuntime.field(b, 2, 2), CalciteRuntime.field(b, 2, 3)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default RewriteLateralSubquery toRule() {
			return new RewriteLateralSubquery(this);
		}

		@Override
		default String description() {
			return "RewriteLateralSubquery";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalCorrelate.class).predicate(r_0 -> r_0.getJoinType() == JoinRelType.LEFT).inputs(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2).anyInputs(), s_2 -> s_2.operand(LogicalFilter.class).oneInput(s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 2).anyInputs()));
		}
	}
}
