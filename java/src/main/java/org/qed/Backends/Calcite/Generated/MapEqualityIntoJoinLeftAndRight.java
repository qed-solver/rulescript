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

public class MapEqualityIntoJoinLeftAndRight extends RelRule<MapEqualityIntoJoinLeftAndRight.Config> {
	protected MapEqualityIntoJoinLeftAndRight(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = CalciteRuntime.conjunctions(e_0);
		if (!(c_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		if (!(e_2 instanceof RexCall && ((RexCall) e_2).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_2).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_2).getOperands().get(0);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_2).getOperands().get(1);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 2)) {
			return;
		}
		final RexNode e_5 = c_1.get(1);
		if (!(e_5 instanceof RexCall && ((RexCall) e_5).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_5).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_6 = ((RexCall) e_5).getOperands().get(0);
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 2)) {
			return;
		}
		final RexNode e_7 = ((RexCall) e_5).getOperands().get(1);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Key_Type", false, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Key_Type", false, r_1, 1))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "Key_Type", false, r_2, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_2);
		b.join(JoinRelType.INNER, b.and(b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 2), CalciteRuntime.field(b, 2, 1)), b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default MapEqualityIntoJoinLeftAndRight toRule() {
			return new MapEqualityIntoJoinLeftAndRight(this);
		}

		@Override
		default String description() {
			return "MapEqualityIntoJoinLeftAndRight";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).predicate(r_0 -> r_0.getJoinType() == JoinRelType.INNER).inputs(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2 && !r_1.getRowType().getFieldList().get(0).getType().isNullable() && !r_1.getRowType().getFieldList().get(1).getType().isNullable()).anyInputs(), s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1 && !r_2.getRowType().getFieldList().get(0).getType().isNullable()).anyInputs());
		}
	}
}
