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

public class TransposeWindow extends RelRule<TransposeWindow.Config> {
	protected TransposeWindow(Config config) {
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
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 3)) {
			return;
		}
		final RexNode e_4 = r_0.getProjects().get(4);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 5)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		final RexNode e_5 = r_1.getCondition();
		if (!(e_5 instanceof RexCall && ((RexCall) e_5).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_5).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_6 = ((RexCall) e_5).getOperands().get(0);
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 0)) {
			return;
		}
		final RexNode e_7 = ((RexCall) e_5).getOperands().get(1);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 4)) {
			return;
		}
		final LogicalProject r_2 = call.rel(2);
		final RexNode e_8 = r_2.getProjects().get(0);
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 0)) {
			return;
		}
		final RexNode e_9 = r_2.getProjects().get(1);
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 1)) {
			return;
		}
		final RexNode e_10 = r_2.getProjects().get(2);
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 2)) {
			return;
		}
		final RexNode e_11 = r_2.getProjects().get(3);
		if (!(e_11 instanceof RexInputRef && ((RexInputRef) e_11).getIndex() == 5)) {
			return;
		}
		final LogicalJoin r_3 = call.rel(3);
		final RexNode e_12 = r_3.getCondition();
		final List<RexNode> c_13 = CalciteRuntime.conjunctions(e_12);
		if (!(c_13.size() == 2)) {
			return;
		}
		final RexNode e_14 = c_13.get(0);
		if (!(e_14 instanceof RexCall && ((RexCall) e_14).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_14).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_15 = ((RexCall) e_14).getOperands().get(0);
		if (!(e_15 instanceof RexInputRef && ((RexInputRef) e_15).getIndex() == 0)) {
			return;
		}
		final RexNode e_16 = ((RexCall) e_14).getOperands().get(1);
		if (!(e_16 instanceof RexInputRef && ((RexInputRef) e_16).getIndex() == 3)) {
			return;
		}
		final RexNode e_17 = c_13.get(1);
		if (!(e_17 instanceof RexCall && ((RexCall) e_17).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_17).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_18 = ((RexCall) e_17).getOperands().get(0);
		if (!(e_18 instanceof RexInputRef && ((RexInputRef) e_18).getIndex() == 1)) {
			return;
		}
		final RexNode e_19 = ((RexCall) e_17).getOperands().get(1);
		if (!(e_19 instanceof RexInputRef && ((RexInputRef) e_19).getIndex() == 4)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(CalciteRuntime.bindColumn(types, "a_type", true, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "b_type", true, r_4, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "p_type", true, r_4, 2))) {
			return;
		}
		final LogicalAggregate r_5 = call.rel(5);
		if (!(r_5.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_5.getGroupSet().cardinality() == 2)) {
			return;
		}
		if (!(r_5.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_20 = r_5.getGroupSet().asList();
		final RexNode e_21 = CalciteRuntime.ref(g_20.get(0));
		if (!(e_21 instanceof RexInputRef && ((RexInputRef) e_21).getIndex() == 0)) {
			return;
		}
		final RexNode e_22 = CalciteRuntime.ref(g_20.get(1));
		if (!(e_22 instanceof RexInputRef && ((RexInputRef) e_22).getIndex() == 1)) {
			return;
		}
		final AggregateCall a_23 = r_5.getAggCallList().get(0);
		if (!(!a_23.hasFilter() && a_23.getCollation().getFieldCollations().isEmpty() && a_23.isDistinct() == false && a_23.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_24 = CalciteRuntime.ref(a_23.getArgList().get(0));
		if (!(e_24 instanceof RexInputRef && ((RexInputRef) e_24).getIndex() == 2)) {
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
		final List<Integer> g_25 = r_7.getGroupSet().asList();
		final RexNode e_26 = CalciteRuntime.ref(g_25.get(0));
		if (!(e_26 instanceof RexInputRef && ((RexInputRef) e_26).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_27 = r_7.getAggCallList().get(0);
		if (!(!a_27.hasFilter() && a_27.getCollation().getFieldCollations().isEmpty() && a_27.isDistinct() == false && a_27.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_28 = CalciteRuntime.ref(a_27.getArgList().get(0));
		if (!(e_28 instanceof RexInputRef && ((RexInputRef) e_28).getIndex() == 2)) {
			return;
		}
		final RelNode r_8 = call.rel(8);
		if (!(r_8.deepEquals(r_4))) {
			return;
		}
		b.push(r_4);
		b.push(r_4);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_27.getAggregation(), false, null, a_27.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 3)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 4)));
		b.push(r_4);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)), b.aggregateCall((SqlAggFunction) a_23.getAggregation(), false, null, a_23.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		b.join(JoinRelType.INNER, b.and(b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 4)), b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 1), CalciteRuntime.field(b, 2, 5))));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 6), CalciteRuntime.field(b, 1, 3)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default TransposeWindow toRule() {
			return new TransposeWindow(this);
		}

		@Override
		default String description() {
			return "TransposeWindow";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 5).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(LogicalProject.class).predicate(r_2 -> r_2.getProjects().size() == 4).oneInput(s_3 -> s_3.operand(LogicalJoin.class).predicate(r_3 -> r_3.getJoinType() == JoinRelType.INNER).inputs(s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 3).anyInputs(), s_5 -> s_5.operand(LogicalAggregate.class).oneInput(s_6 -> s_6.operand(RelNode.class).anyInputs()))), s_7 -> s_7.operand(LogicalAggregate.class).oneInput(s_8 -> s_8.operand(RelNode.class).anyInputs())));
		}
	}
}
