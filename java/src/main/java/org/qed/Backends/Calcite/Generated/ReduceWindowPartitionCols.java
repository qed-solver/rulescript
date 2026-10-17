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

public class ReduceWindowPartitionCols extends RelRule<ReduceWindowPartitionCols.Config> {
	protected ReduceWindowPartitionCols(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		final RexNode e_0 = r_0.getProjects().get(0);
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(5)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		final RexNode e_3 = r_1.getCondition();
		final List<RexNode> c_4 = CalciteRuntime.conjunctions(e_3);
		if (!(c_4.size() == 2)) {
			return;
		}
		final RexNode e_5 = c_4.get(0);
		if (!(e_5 instanceof RexCall && ((RexCall) e_5).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_5).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_6 = ((RexCall) e_5).getOperands().get(0);
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 0)) {
			return;
		}
		final RexNode e_7 = ((RexCall) e_5).getOperands().get(1);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 3)) {
			return;
		}
		final RexNode e_8 = c_4.get(1);
		if (!(e_8 instanceof RexCall && ((RexCall) e_8).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_8).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_9 = ((RexCall) e_8).getOperands().get(0);
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 1)) {
			return;
		}
		final RexNode e_10 = ((RexCall) e_8).getOperands().get(1);
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 4)) {
			return;
		}
		final LogicalProject r_2 = call.rel(2);
		final RexNode e_11 = r_2.getProjects().get(0);
		if (!(e_11 instanceof RexInputRef && ((RexInputRef) e_11).getIndex() == 0)) {
			return;
		}
		final RexNode e_12 = r_2.getProjects().get(1);
		final RexNode[] args_13 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_14 = e_12;
		if (!(CalciteRuntime.abstracts(sym_14, args_13))) {
			return;
		}
		final RexNode e_15 = r_2.getProjects().get(2);
		if (!(e_15 instanceof RexInputRef && ((RexInputRef) e_15).getIndex() == 1)) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(CalciteRuntime.bindColumn(types, "K_Type", true, r_3, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V_Type", true, r_3, 1))) {
			return;
		}
		final LogicalAggregate r_4 = call.rel(4);
		if (!(r_4.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_4.getGroupSet().cardinality() == 2)) {
			return;
		}
		if (!(r_4.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_16 = r_4.getGroupSet().asList();
		final RexNode e_17 = CalciteRuntime.ref(g_16.get(0));
		if (!(e_17 instanceof RexInputRef && ((RexInputRef) e_17).getIndex() == 0)) {
			return;
		}
		final RexNode e_18 = CalciteRuntime.ref(g_16.get(1));
		if (!(e_18 instanceof RexInputRef && ((RexInputRef) e_18).getIndex() == 1)) {
			return;
		}
		final AggregateCall a_19 = r_4.getAggCallList().get(0);
		if (!(!a_19.hasFilter() && a_19.getCollation().getFieldCollations().isEmpty() && a_19.isDistinct() == false && a_19.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_20 = CalciteRuntime.ref(a_19.getArgList().get(0));
		if (!(e_20 instanceof RexInputRef && ((RexInputRef) e_20).getIndex() == 2)) {
			return;
		}
		final LogicalProject r_5 = call.rel(5);
		final RexNode e_21 = r_5.getProjects().get(0);
		if (!(e_21 instanceof RexInputRef && ((RexInputRef) e_21).getIndex() == 0)) {
			return;
		}
		final RexNode e_22 = r_5.getProjects().get(1);
		if (!(sym_14.equals(e_22))) {
			return;
		}
		final RexNode e_23 = r_5.getProjects().get(2);
		if (!(e_23 instanceof RexInputRef && ((RexInputRef) e_23).getIndex() == 1)) {
			return;
		}
		final RelNode r_6 = call.rel(6);
		if (!(r_6.deepEquals(r_3))) {
			return;
		}
		b.push(r_3);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.field(b, 1, 1)));
		b.push(r_3);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.field(b, 1, 1)));
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_19.getAggregation(), false, null, a_19.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.field(b, 1, 1)));
		b.join(JoinRelType.INNER, b.and(b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 3)), b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 1), CalciteRuntime.field(b, 2, 4))));
		b.project(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 5)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ReduceWindowPartitionCols toRule() {
			return new ReduceWindowPartitionCols(this);
		}

		@Override
		default String description() {
			return "ReduceWindowPartitionCols";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 1).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(LogicalProject.class).predicate(r_2 -> r_2.getProjects().size() == 3).oneInput(s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 2).anyInputs()), s_4 -> s_4.operand(LogicalAggregate.class).oneInput(s_5 -> s_5.operand(LogicalProject.class).predicate(r_5 -> r_5.getProjects().size() == 3).oneInput(s_6 -> s_6.operand(RelNode.class).anyInputs()))));
		}
	}
}
