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

public class CollapseWindow extends RelRule<CollapseWindow.Config> {
	protected CollapseWindow(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 0)) {
			return;
		}
		final RexNode e_1 = r_0.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 1)) {
			return;
		}
		final RexNode e_2 = r_0.getProjects().get(2);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 2)) {
			return;
		}
		final RexNode e_3 = r_0.getProjects().get(3);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 4)) {
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
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 3)) {
			return;
		}
		final LogicalProject r_2 = call.rel(2);
		final RexNode e_7 = r_2.getProjects().get(0);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 0)) {
			return;
		}
		final RexNode e_8 = r_2.getProjects().get(1);
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 1)) {
			return;
		}
		final RexNode e_9 = r_2.getProjects().get(2);
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 3)) {
			return;
		}
		final LogicalJoin r_3 = call.rel(3);
		final RexNode e_10 = r_3.getCondition();
		if (!(e_10 instanceof RexCall && ((RexCall) e_10).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_10).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_11 = ((RexCall) e_10).getOperands().get(0);
		if (!(e_11 instanceof RexInputRef && ((RexInputRef) e_11).getIndex() == 0)) {
			return;
		}
		final RexNode e_12 = ((RexCall) e_10).getOperands().get(1);
		if (!(e_12 instanceof RexInputRef && ((RexInputRef) e_12).getIndex() == 2)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(CalciteRuntime.bindColumn(types, "k", false, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "v", false, r_4, 1))) {
			return;
		}
		final LogicalAggregate r_5 = call.rel(5);
		if (!(r_5.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_5.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_5.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_13 = r_5.getGroupSet().asList();
		final RexNode e_14 = CalciteRuntime.ref(g_13.get(0));
		if (!(e_14 instanceof RexInputRef && ((RexInputRef) e_14).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_15 = r_5.getAggCallList().get(0);
		if (!(!a_15.hasFilter() && a_15.getCollation().getFieldCollations().isEmpty() && a_15.isDistinct() == false && a_15.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_16 = CalciteRuntime.ref(a_15.getArgList().get(0));
		if (!(e_16 instanceof RexInputRef && ((RexInputRef) e_16).getIndex() == 1)) {
			return;
		}
		final RelNode r_6 = call.rel(6);
		if (!(r_6.deepEquals(r_4))) {
			return;
		}
		final LogicalAggregate r_7 = call.rel(7);
		if (!(r_7.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_7.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_7.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_17 = r_7.getGroupSet().asList();
		final RexNode e_18 = CalciteRuntime.ref(g_17.get(0));
		if (!(e_18 instanceof RexInputRef && ((RexInputRef) e_18).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_19 = r_7.getAggCallList().get(0);
		if (!(!a_19.hasFilter() && a_19.getCollation().getFieldCollations().isEmpty() && a_19.isDistinct() == false && a_19.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_20 = CalciteRuntime.ref(a_19.getArgList().get(0));
		if (!(e_20 instanceof RexInputRef && ((RexInputRef) e_20).getIndex() == 1)) {
			return;
		}
		final RelNode r_8 = call.rel(8);
		if (!(r_8.deepEquals(r_4))) {
			return;
		}
		b.push(r_4);
		b.push(r_4);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_15.getAggregation(), false, null, a_15.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))), b.aggregateCall((SqlAggFunction) a_19.getAggregation(), false, null, a_19.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 2)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 3), CalciteRuntime.field(b, 1, 4)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default CollapseWindow toRule() {
			return new CollapseWindow(this);
		}

		@Override
		default String description() {
			return "CollapseWindow";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 4).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(LogicalProject.class).predicate(r_2 -> r_2.getProjects().size() == 3).oneInput(s_3 -> s_3.operand(LogicalJoin.class).predicate(r_3 -> r_3.getJoinType() == JoinRelType.INNER).inputs(s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 2 && !r_4.getRowType().getFieldList().get(0).getType().isNullable() && !r_4.getRowType().getFieldList().get(1).getType().isNullable()).anyInputs(), s_5 -> s_5.operand(LogicalAggregate.class).oneInput(s_6 -> s_6.operand(RelNode.class).anyInputs()))), s_7 -> s_7.operand(LogicalAggregate.class).oneInput(s_8 -> s_8.operand(RelNode.class).anyInputs())));
		}
	}
}
