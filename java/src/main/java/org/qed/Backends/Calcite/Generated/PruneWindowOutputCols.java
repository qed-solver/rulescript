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

public class PruneWindowOutputCols extends RelRule<PruneWindowOutputCols.Config> {
	protected PruneWindowOutputCols(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		if (!(r_0.getProjects().size() == 3)) {
			return;
		}
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 0)) {
			return;
		}
		final RexNode e_1 = r_0.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 1)) {
			return;
		}
		final RexNode e_2 = r_0.getProjects().get(2);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 3)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		if (!(r_1.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_3 = r_1.getCondition();
		if (!(e_3 instanceof RexCall && ((RexCall) e_3).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_3).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_3).getOperands().get(0);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 0)) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_3).getOperands().get(1);
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 2)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 2)) {
			return;
		}
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
		if (!(r_3.getAggCallList().size() == 2)) {
			return;
		}
		final List<Integer> g_6 = r_3.getGroupSet().asList();
		final RexNode e_7 = CalciteRuntime.ref(g_6.get(0));
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_8 = r_3.getAggCallList().get(0);
		if (!(!a_8.hasFilter() && a_8.getCollation().getFieldCollations().isEmpty() && a_8.isDistinct() == false && a_8.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_9 = CalciteRuntime.ref(a_8.getArgList().get(0));
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 1)) {
			return;
		}
		final AggregateCall a_10 = r_3.getAggCallList().get(1);
		if (!(!a_10.hasFilter() && a_10.getCollation().getFieldCollations().isEmpty() && a_10.isDistinct() == false && a_10.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_11 = CalciteRuntime.ref(a_10.getArgList().get(0));
		if (!(e_11 instanceof RexInputRef && ((RexInputRef) e_11).getIndex() == 1)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.deepEquals(r_2))) {
			return;
		}
		b.push(r_2);
		b.push(r_2);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_8.getAggregation(), false, null, a_8.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		b.join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 2)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 3)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneWindowOutputCols toRule() {
			return new PruneWindowOutputCols(this);
		}

		@Override
		default String description() {
			return "PruneWindowOutputCols";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).inputs(s_2 -> s_2.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(LogicalAggregate.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs())));
		}
	}
}
