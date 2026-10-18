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

public class UnionToValues extends RelRule<UnionToValues.Config> {
	protected UnionToValues(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalUnion r_0 = call.rel(0);
		final LogicalValues r_1 = call.rel(1);
		if (!(CalciteRuntime.sameValues(call, r_1, new String[]{"c0", "c1"}, 1, 2, 3, 4))) {
			return;
		}
		final LogicalValues r_2 = call.rel(2);
		if (!(CalciteRuntime.sameValues(call, r_2, new String[]{"c0", "c1"}, 3, 4, 5, 6))) {
			return;
		}
		final LogicalValues r_3 = call.rel(3);
		if (!(CalciteRuntime.sameValues(call, r_3, new String[]{"c0", "c1"}, 7, 8))) {
			return;
		}
		b.values(new String[]{"c0", "c1"}, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8);
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default UnionToValues toRule() {
			return new UnionToValues(this);
		}

		@Override
		default String description() {
			return "UnionToValues";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalUnion.class).predicate(r_0 -> r_0.all == true && r_0.getInputs().size() == 3).inputs(s_1 -> s_1.operand(LogicalValues.class).noInputs(), s_2 -> s_2.operand(LogicalValues.class).noInputs(), s_3 -> s_3.operand(LogicalValues.class).noInputs());
		}
	}
}
