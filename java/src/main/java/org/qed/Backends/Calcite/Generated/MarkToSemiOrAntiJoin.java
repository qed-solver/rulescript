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

public class MarkToSemiOrAntiJoin extends RelRule<MarkToSemiOrAntiJoin.Config> {
	protected MarkToSemiOrAntiJoin(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		final RexNode e_0 = r_0.getProjects().get(0);
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final LogicalFilter r_1 = call.rel(1);
		final RexNode e_3 = r_1.getCondition();
		final List<RexNode> c_4 = CalciteRuntime.conjunctions(e_3);
		if (!(c_4.size() == 2)) {
			return;
		}
		final RexNode e_5 = c_4.get(0);
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 1)) {
			return;
		}
		final RexNode e_6 = c_4.get(1);
		final RexNode[] args_7 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_8 = e_6;
		if (!(CalciteRuntime.abstracts(sym_8, args_7))) {
			return;
		}
		final LogicalUnion r_2 = call.rel(2);
		final LogicalProject r_3 = call.rel(3);
		final RexNode e_9 = r_3.getProjects().get(0);
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 0)) {
			return;
		}
		final RexNode e_10 = r_3.getProjects().get(1);
		if (!(e_10.isAlwaysTrue())) {
			return;
		}
		final LogicalJoin r_4 = call.rel(4);
		final RexNode e_11 = r_4.getCondition();
		final RexNode[] args_12 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_13 = e_11;
		if (!(CalciteRuntime.abstracts(sym_13, args_12))) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(CalciteRuntime.bindColumn(types, "L_T", true, r_5, 0))) {
			return;
		}
		final RelNode r_6 = call.rel(6);
		if (!(CalciteRuntime.bindColumn(types, "R_T", true, r_6, 0))) {
			return;
		}
		final LogicalProject r_7 = call.rel(7);
		final RexNode e_14 = r_7.getProjects().get(0);
		if (!(e_14 instanceof RexInputRef && ((RexInputRef) e_14).getIndex() == 0)) {
			return;
		}
		final RexNode e_15 = r_7.getProjects().get(1);
		if (!(e_15.isAlwaysFalse())) {
			return;
		}
		final LogicalJoin r_8 = call.rel(8);
		final RexNode e_16 = r_8.getCondition();
		if (!(sym_13.equals(e_16))) {
			return;
		}
		final RelNode r_9 = call.rel(9);
		if (!(r_9.deepEquals(r_5))) {
			return;
		}
		final RelNode r_10 = call.rel(10);
		if (!(r_10.deepEquals(r_6))) {
			return;
		}
		b.push(r_5);
		b.push(r_6);
		b.join(JoinRelType.SEMI, CalciteRuntime.instantiate(sym_13, args_12, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)}));
		b.filter(CalciteRuntime.instantiate(sym_8, args_7, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.project(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default MarkToSemiOrAntiJoin toRule() {
			return new MarkToSemiOrAntiJoin(this);
		}

		@Override
		default String description() {
			return "MarkToSemiOrAntiJoin";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 1).oneInput(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_2 -> s_2.operand(LogicalUnion.class).predicate(r_2 -> r_2.all == true && r_2.getInputs().size() == 2).inputs(s_3 -> s_3.operand(LogicalProject.class).predicate(r_3 -> r_3.getProjects().size() == 2).oneInput(s_4 -> s_4.operand(LogicalJoin.class).predicate(r_4 -> r_4.getJoinType() == JoinRelType.SEMI).inputs(s_5 -> s_5.operand(RelNode.class).predicate(r_5 -> r_5.getRowType().getFieldCount() == 1).anyInputs(), s_6 -> s_6.operand(RelNode.class).predicate(r_6 -> r_6.getRowType().getFieldCount() == 1).anyInputs())), s_7 -> s_7.operand(LogicalProject.class).predicate(r_7 -> r_7.getProjects().size() == 2).oneInput(s_8 -> s_8.operand(LogicalJoin.class).predicate(r_8 -> r_8.getJoinType() == JoinRelType.ANTI).inputs(s_9 -> s_9.operand(RelNode.class).anyInputs(), s_10 -> s_10.operand(RelNode.class).anyInputs())))));
		}
	}
}
