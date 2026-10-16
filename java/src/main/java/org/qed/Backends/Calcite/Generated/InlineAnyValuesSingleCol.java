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

public class InlineAnyValuesSingleCol extends RelRule<InlineAnyValuesSingleCol.Config> {
	protected InlineAnyValuesSingleCol(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexCall && ((RexCall) e_0).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_0).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_1 = ((RexCall) e_0).getOperands().get(0);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final RexNode e_2 = ((RexCall) e_0).getOperands().get(1);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "S_Type", true, r_1, 0))) {
			return;
		}
		final LogicalUnion r_2 = call.rel(2);
		final LogicalValues r_3 = call.rel(3);
		if (!(CalciteRuntime.sameValues(call, r_3, new String[]{"c0"}, 1))) {
			return;
		}
		final LogicalValues r_4 = call.rel(4);
		if (!(CalciteRuntime.sameValues(call, r_4, new String[]{"c0"}, 3))) {
			return;
		}
		b.push(r_1);
		final RexNode cond_3 = b.call(SqlStdOperatorTable.OR, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 1, 0), b.literal(1)), b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 1, 0), b.literal(3)));
		b.push(LogicalFilter.create(b.build(), cond_3));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default InlineAnyValuesSingleCol toRule() {
			return new InlineAnyValuesSingleCol(this);
		}

		@Override
		default String description() {
			return "InlineAnyValuesSingleCol";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).predicate(r_0 -> r_0.getJoinType() == JoinRelType.SEMI).inputs(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs(), s_2 -> s_2.operand(LogicalUnion.class).predicate(r_2 -> r_2.all == true && r_2.getInputs().size() == 2).inputs(s_3 -> s_3.operand(LogicalValues.class).noInputs(), s_4 -> s_4.operand(LogicalValues.class).noInputs()));
		}
	}
}
