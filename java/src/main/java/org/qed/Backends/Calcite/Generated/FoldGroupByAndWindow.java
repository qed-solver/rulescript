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

public class FoldGroupByAndWindow extends RelRule<FoldGroupByAndWindow.Config> {
	protected FoldGroupByAndWindow(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalAggregate r_0 = call.rel(0);
		if (!(r_0.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_0.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_0.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final RexNode e_1 = CalciteRuntime.ref(g_0.get(0));
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_2 = r_0.getAggCallList().get(0);
		if (!(!a_2.hasFilter() && a_2.getCollation().getFieldCollations().isEmpty() && a_2.isDistinct() == false && a_2.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_3 = CalciteRuntime.ref(a_2.getArgList().get(0));
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 1)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		final RexNode e_4 = r_1.getCondition();
		if (!(e_4 instanceof RexCall && ((RexCall) e_4).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_4).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_4).getOperands().get(0);
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 0)) {
			return;
		}
		final RexNode e_6 = ((RexCall) e_4).getOperands().get(1);
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 2)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "k", false, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "v", false, r_2, 1))) {
			return;
		}
		final LogicalAggregate r_3 = call.rel(3);
		if (!(r_3.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_3.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_3.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_7 = r_3.getGroupSet().asList();
		final RexNode e_8 = CalciteRuntime.ref(g_7.get(0));
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_9 = r_3.getAggCallList().get(0);
		if (!(!a_9.hasFilter() && a_9.getCollation().getFieldCollations().isEmpty() && a_9.isDistinct() == false && a_9.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_10 = CalciteRuntime.ref(a_9.getArgList().get(0));
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 1)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.deepEquals(r_2))) {
			return;
		}
		b.push(r_2);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_2.getAggregation(), false, null, a_2.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default FoldGroupByAndWindow toRule() {
			return new FoldGroupByAndWindow(this);
		}

		@Override
		default String description() {
			return "FoldGroupByAndWindow";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 2 && !r_2.getRowType().getFieldList().get(0).getType().isNullable() && !r_2.getRowType().getFieldList().get(1).getType().isNullable()).anyInputs(), s_3 -> s_3.operand(LogicalAggregate.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs())));
		}
	}
}
