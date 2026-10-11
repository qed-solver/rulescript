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

public class AggregateExtractProject extends RelRule<AggregateExtractProject.Config> {
	protected AggregateExtractProject(Config config) {
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
		if (!(e_4.isAlwaysTrue())) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		final RexNode e_5 = r_2.getCondition();
		if (!(e_5.isAlwaysTrue())) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_4, 0))) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_5, 0))) {
			return;
		}
		b.push(r_3);
		b.push(r_4);
		b.join(JoinRelType.INNER, b.literal(true));
		b.push(r_5);
		b.join(JoinRelType.INNER, b.literal(true));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_2.getAggregation(), false, null, a_2.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateExtractProject toRule() {
			return new AggregateExtractProject(this);
		}

		@Override
		default String description() {
			return "AggregateExtractProject";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).predicate(r_1 -> r_1.getJoinType() == JoinRelType.INNER).inputs(s_2 -> s_2.operand(LogicalJoin.class).predicate(r_2 -> r_2.getJoinType() == JoinRelType.INNER).inputs(s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 1).anyInputs(), s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 1).anyInputs()), s_5 -> s_5.operand(RelNode.class).predicate(r_5 -> r_5.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
