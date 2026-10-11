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

public class SetOpToFilter extends RelRule<SetOpToFilter.Config> {
	protected SetOpToFilter(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalUnion r_0 = call.rel(0);
		if (!(r_0.all == false)) {
			return;
		}
		if (!(r_0.getInputs().size() == 2)) {
			return;
		}
		final LogicalFilter r_1 = call.rel(1);
		final RexNode e_0 = r_1.getCondition();
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_2, 0))) {
			return;
		}
		final LogicalFilter r_3 = call.rel(3);
		final RexNode e_3 = r_3.getCondition();
		final RexNode[] args_4 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.deepEquals(r_2))) {
			return;
		}
		b.push(r_2);
		b.filter(b.or(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{CalciteRuntime.field(b, 1, 0)})));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default SetOpToFilter toRule() {
			return new SetOpToFilter(this);
		}

		@Override
		default String description() {
			return "SetOpToFilter";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalUnion.class).inputs(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()), s_3 -> s_3.operand(LogicalFilter.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs()));
		}
	}
}
