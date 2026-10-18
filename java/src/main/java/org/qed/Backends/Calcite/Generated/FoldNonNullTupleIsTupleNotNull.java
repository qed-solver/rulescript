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

public class FoldNonNullTupleIsTupleNotNull extends RelRule<FoldNonNullTupleIsTupleNotNull.Config> {
	protected FoldNonNullTupleIsTupleNotNull(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = CalciteRuntime.conjunctions(e_0);
		if (!(c_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		if (!(e_2 instanceof RexCall && ((RexCall) e_2).getOperator() == SqlStdOperatorTable.IS_NOT_NULL && ((RexCall) e_2).getOperands().size() == 1)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_2).getOperands().get(0);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RexNode e_4 = c_1.get(1);
		if (!(e_4 instanceof RexCall && ((RexCall) e_4).getOperator() == SqlStdOperatorTable.IS_NOT_NULL && ((RexCall) e_4).getOperands().size() == 1)) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_4).getOperands().get(0);
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Elem0_Type", false, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Elem1_Type", false, r_1, 1))) {
			return;
		}
		b.push(r_1);
		b.filter(b.literal(true));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default FoldNonNullTupleIsTupleNotNull toRule() {
			return new FoldNonNullTupleIsTupleNotNull(this);
		}

		@Override
		default String description() {
			return "FoldNonNullTupleIsTupleNotNull";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2 && !r_1.getRowType().getFieldList().get(0).getType().isNullable() && !r_1.getRowType().getFieldList().get(1).getType().isNullable()).anyInputs());
		}
	}
}
