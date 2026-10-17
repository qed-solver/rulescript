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

public class WindowReduceExpressions extends RelRule<WindowReduceExpressions.Config> {
	protected WindowReduceExpressions(Config config) {
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
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 2)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "K_Type", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V_Type", true, r_1, 1))) {
			return;
		}
		final LogicalAggregate r_2 = call.rel(2);
		if (!(r_2.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_2.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_2.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_3 = r_2.getGroupSet().asList();
		final RexNode e_4 = CalciteRuntime.ref(g_3.get(0));
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_5 = r_2.getAggCallList().get(0);
		if (!(!a_5.hasFilter() && a_5.getCollation().getFieldCollations().isEmpty() && a_5.isDistinct() == false && a_5.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_6 = CalciteRuntime.ref(a_5.getArgList().get(0));
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 1)) {
			return;
		}
		final LogicalProject r_3 = call.rel(3);
		final RexNode e_7 = r_3.getProjects().get(0);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 0)) {
			return;
		}
		final RexNode e_8 = r_3.getProjects().get(1);
		final List<RexNode> c_9 = CalciteRuntime.conjunctions(e_8);
		if (!(c_9.size() == 2)) {
			return;
		}
		final RexNode e_10 = c_9.get(0);
		if (!(e_10.isAlwaysFalse())) {
			return;
		}
		final RexNode e_11 = c_9.get(1);
		final RexNode[] args_12 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_13 = e_11;
		if (!(CalciteRuntime.abstracts(sym_13, args_12))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.deepEquals(r_1))) {
			return;
		}
		b.push(r_1);
		b.push(r_1);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), b.literal(false)));
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_5.getAggregation(), false, null, a_5.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 2)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default WindowReduceExpressions toRule() {
			return new WindowReduceExpressions(this);
		}

		@Override
		default String description() {
			return "WindowReduceExpressions";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).predicate(r_0 -> r_0.getJoinType() == JoinRelType.INNER).inputs(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2).anyInputs(), s_2 -> s_2.operand(LogicalAggregate.class).oneInput(s_3 -> s_3.operand(LogicalProject.class).predicate(r_3 -> r_3.getProjects().size() == 2).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs())));
		}
	}
}
