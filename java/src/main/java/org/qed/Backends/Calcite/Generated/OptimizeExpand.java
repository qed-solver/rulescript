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

public class OptimizeExpand extends RelRule<OptimizeExpand.Config> {
	protected OptimizeExpand(Config config) {
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
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 3)) {
			return;
		}
		final LogicalAggregate r_1 = call.rel(1);
		if (!(r_1.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_1.getGroupSet().cardinality() == 3)) {
			return;
		}
		if (!(r_1.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_4 = r_1.getGroupSet().asList();
		final RexNode e_5 = CalciteRuntime.ref(g_4.get(0));
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 0)) {
			return;
		}
		final RexNode e_6 = CalciteRuntime.ref(g_4.get(1));
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 1)) {
			return;
		}
		final RexNode e_7 = CalciteRuntime.ref(g_4.get(2));
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 2)) {
			return;
		}
		final AggregateCall a_8 = r_1.getAggCallList().get(0);
		if (!(!a_8.hasFilter() && a_8.getCollation().getFieldCollations().isEmpty() && a_8.isDistinct() == false && a_8.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_9 = CalciteRuntime.ref(a_8.getArgList().get(0));
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 3)) {
			return;
		}
		final LogicalAggregate r_2 = call.rel(2);
		if (!(r_2.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_2.getGroupSet().cardinality() == 4)) {
			return;
		}
		if (!(r_2.getAggCallList().size() == 0)) {
			return;
		}
		final List<Integer> g_10 = r_2.getGroupSet().asList();
		final RexNode e_11 = CalciteRuntime.ref(g_10.get(0));
		if (!(e_11 instanceof RexInputRef && ((RexInputRef) e_11).getIndex() == 0)) {
			return;
		}
		final RexNode e_12 = CalciteRuntime.ref(g_10.get(1));
		if (!(e_12 instanceof RexInputRef && ((RexInputRef) e_12).getIndex() == 1)) {
			return;
		}
		final RexNode e_13 = CalciteRuntime.ref(g_10.get(2));
		if (!(e_13 instanceof RexInputRef && ((RexInputRef) e_13).getIndex() == 2)) {
			return;
		}
		final RexNode e_14 = CalciteRuntime.ref(g_10.get(3));
		if (!(e_14 instanceof RexInputRef && ((RexInputRef) e_14).getIndex() == 3)) {
			return;
		}
		final LogicalUnion r_3 = call.rel(3);
		final LogicalProject r_4 = call.rel(4);
		final RexNode e_15 = r_4.getProjects().get(0);
		if (!(e_15 instanceof RexInputRef && ((RexInputRef) e_15).getIndex() == 0)) {
			return;
		}
		final RexNode e_16 = r_4.getProjects().get(1);
		final RexNode[] args_17 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_18 = e_16;
		if (!(CalciteRuntime.abstracts(sym_18, args_17))) {
			return;
		}
		final RexNode e_19 = r_4.getProjects().get(2);
		final RexNode[] args_20 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_21 = e_19;
		if (!(CalciteRuntime.abstracts(sym_21, args_20))) {
			return;
		}
		final RexNode e_22 = r_4.getProjects().get(3);
		if (!(e_22 instanceof RexInputRef && ((RexInputRef) e_22).getIndex() == 3)) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(CalciteRuntime.bindColumn(types, "Key", true, r_5, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Cat1", true, r_5, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Cat2", true, r_5, 2))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Value", true, r_5, 3))) {
			return;
		}
		final LogicalProject r_6 = call.rel(6);
		final RexNode e_23 = r_6.getProjects().get(0);
		if (!(e_23 instanceof RexInputRef && ((RexInputRef) e_23).getIndex() == 0)) {
			return;
		}
		final RexNode e_24 = r_6.getProjects().get(1);
		final RexNode[] args_25 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_26 = e_24;
		if (!(CalciteRuntime.abstracts(sym_26, args_25))) {
			return;
		}
		final RexNode e_27 = r_6.getProjects().get(2);
		final RexNode[] args_28 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_29 = e_27;
		if (!(CalciteRuntime.abstracts(sym_29, args_28))) {
			return;
		}
		final RexNode e_30 = r_6.getProjects().get(3);
		if (!(e_30 instanceof RexInputRef && ((RexInputRef) e_30).getIndex() == 3)) {
			return;
		}
		final RelNode r_7 = call.rel(7);
		if (!(r_7.deepEquals(r_5))) {
			return;
		}
		final LogicalProject r_8 = call.rel(8);
		final RexNode e_31 = r_8.getProjects().get(0);
		if (!(e_31 instanceof RexInputRef && ((RexInputRef) e_31).getIndex() == 0)) {
			return;
		}
		final RexNode e_32 = r_8.getProjects().get(1);
		final RexNode[] args_33 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_34 = e_32;
		if (!(CalciteRuntime.abstracts(sym_34, args_33))) {
			return;
		}
		final RexNode e_35 = r_8.getProjects().get(2);
		final RexNode[] args_36 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2), CalciteRuntime.ref(3)};
		final RexNode sym_37 = e_35;
		if (!(CalciteRuntime.abstracts(sym_37, args_36))) {
			return;
		}
		final RexNode e_38 = r_8.getProjects().get(3);
		if (!(e_38 instanceof RexInputRef && ((RexInputRef) e_38).getIndex() == 3)) {
			return;
		}
		final RelNode r_9 = call.rel(9);
		if (!(r_9.deepEquals(r_5))) {
			return;
		}
		b.push(r_5);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_18, args_17, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)}), CalciteRuntime.instantiate(sym_21, args_20, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)}), CalciteRuntime.field(b, 1, 3)));
		b.push(r_5);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_26, args_25, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)}), CalciteRuntime.instantiate(sym_29, args_28, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)}), CalciteRuntime.field(b, 1, 3)));
		b.push(r_5);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_34, args_33, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)}), CalciteRuntime.instantiate(sym_37, args_36, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)}), CalciteRuntime.field(b, 1, 3)));
		b.union(true, 3);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)), b.aggregateCall((SqlAggFunction) a_8.getAggregation(), false, null, a_8.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 3))));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_2.getAggregation(), false, null, a_2.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 3))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default OptimizeExpand toRule() {
			return new OptimizeExpand(this);
		}

		@Override
		default String description() {
			return "OptimizeExpand";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalAggregate.class).oneInput(s_2 -> s_2.operand(LogicalAggregate.class).oneInput(s_3 -> s_3.operand(LogicalUnion.class).predicate(r_3 -> r_3.all == true && r_3.getInputs().size() == 3).inputs(s_4 -> s_4.operand(LogicalProject.class).predicate(r_4 -> r_4.getProjects().size() == 4).oneInput(s_5 -> s_5.operand(RelNode.class).predicate(r_5 -> r_5.getRowType().getFieldCount() == 4).anyInputs()), s_6 -> s_6.operand(LogicalProject.class).predicate(r_6 -> r_6.getProjects().size() == 4).oneInput(s_7 -> s_7.operand(RelNode.class).anyInputs()), s_8 -> s_8.operand(LogicalProject.class).predicate(r_8 -> r_8.getProjects().size() == 4).oneInput(s_9 -> s_9.operand(RelNode.class).anyInputs())))));
		}
	}
}
