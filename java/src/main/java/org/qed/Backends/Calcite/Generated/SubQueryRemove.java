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

public class SubQueryRemove extends RelRule<SubQueryRemove.Config> {
	protected SubQueryRemove(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexSubQuery && ((RexSubQuery) e_0).getKind() == org.apache.calcite.sql.SqlKind.EXISTS)) {
			return;
		}
		final RelNode r_1000 = (RelNode) ((RexSubQuery) e_0).rel;
		if (!(r_1000.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "S_Type", true, r_1000, 0))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "R_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_1000);
		b.project(b.literal(true));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)));
		b.join(JoinRelType.INNER, b.literal(true));
		b.project(CalciteRuntime.field(b, 1, 0));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default SubQueryRemove toRule() {
			return new SubQueryRemove(this);
		}

		@Override
		default String description() {
			return "SubQueryRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
