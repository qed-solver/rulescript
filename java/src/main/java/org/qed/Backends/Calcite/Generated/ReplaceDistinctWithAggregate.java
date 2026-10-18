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

public class ReplaceDistinctWithAggregate extends RelRule<ReplaceDistinctWithAggregate.Config> {
	protected ReplaceDistinctWithAggregate(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalIntersect r_0 = call.rel(0);
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Col0_Type", false, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Col1_Type", false, r_1, 1))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.deepEquals(r_1))) {
			return;
		}
		b.push(r_1);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ReplaceDistinctWithAggregate toRule() {
			return new ReplaceDistinctWithAggregate(this);
		}

		@Override
		default String description() {
			return "ReplaceDistinctWithAggregate";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalIntersect.class).predicate(r_0 -> r_0.all == false && r_0.getInputs().size() == 2).inputs(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2 && !r_1.getRowType().getFieldList().get(0).getType().isNullable() && !r_1.getRowType().getFieldList().get(1).getType().isNullable()).anyInputs(), s_2 -> s_2.operand(RelNode.class).anyInputs());
		}
	}
}
