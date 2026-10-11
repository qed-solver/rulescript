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

public class FoldNullTupleIsTupleNull extends RelRule<FoldNullTupleIsTupleNull.Config> {
	protected FoldNullTupleIsTupleNull(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = RelOptUtil.conjunctions(e_0);
		if (!(c_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		if (!(e_2 instanceof RexCall && ((RexCall) e_2).getOperator() == SqlStdOperatorTable.IS_NULL && ((RexCall) e_2).getOperands().size() == 1)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_2).getOperands().get(0);
		if (!(RexUtil.isNullLiteral(e_3, true) && e_3.getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName.INTEGER)) {
			return;
		}
		final RexNode e_4 = c_1.get(1);
		if (!(e_4 instanceof RexCall && ((RexCall) e_4).getOperator() == SqlStdOperatorTable.IS_NULL && ((RexCall) e_4).getOperands().size() == 1)) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_4).getOperands().get(0);
		if (!(RexUtil.isNullLiteral(e_5, true) && e_5.getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName.INTEGER)) {
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
		b.filter(b.literal(true));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default FoldNullTupleIsTupleNull toRule() {
			return new FoldNullTupleIsTupleNull(this);
		}

		@Override
		default String description() {
			return "FoldNullTupleIsTupleNull";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
