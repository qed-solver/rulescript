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

public class FoldNeTrue extends RelRule<FoldNeTrue.Config> {
	protected FoldNeTrue(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexCall && ((RexCall) e_0).getOperator() == SqlStdOperatorTable.NOT_EQUALS && ((RexCall) e_0).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_1 = ((RexCall) e_0).getOperands().get(0);
		final RexNode[] args_2 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_3 = e_1;
		if (!(CalciteRuntime.abstracts(sym_3, args_2))) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_0).getOperands().get(1);
		if (!(e_4.isAlwaysTrue())) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.filter(b.not(CalciteRuntime.instantiate(sym_3, args_2, new RexNode[]{CalciteRuntime.field(b, 1, 0)})));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default FoldNeTrue toRule() {
			return new FoldNeTrue(this);
		}

		@Override
		default String description() {
			return "FoldNeTrue";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
