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

public class SimplifyFalseOr extends RelRule<SimplifyFalseOr.Config> {
	protected SimplifyFalseOr(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> d_1 = CalciteRuntime.disjunctions(e_0);
		if (!(d_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = d_1.get(0);
		if (!(e_2.isAlwaysFalse())) {
			return;
		}
		final RexNode e_3 = d_1.get(1);
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.filter(CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default SimplifyFalseOr toRule() {
			return new SimplifyFalseOr(this);
		}

		@Override
		default String description() {
			return "SimplifyFalseOr";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
