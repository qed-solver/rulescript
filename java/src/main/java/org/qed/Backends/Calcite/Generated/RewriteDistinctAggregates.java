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

public class RewriteDistinctAggregates extends RelRule<RewriteDistinctAggregates.Config> {
	protected RewriteDistinctAggregates(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalAggregate r_0 = call.rel(0);
		final LogicalProject r_1 = call.rel(1);
		if (!(r_0.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_0.getGroupSet().cardinality() == 0)) {
			return;
		}
		if (!(r_0.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final AggregateCall a_1 = r_0.getAggCallList().get(0);
		if (!(!a_1.hasFilter() && a_1.getCollation().getFieldCollations().isEmpty() && a_1.isDistinct() == true && a_1.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_2 = r_1.getProjects().get(a_1.getArgList().get(0));
		if (!(CalciteRuntime.isLiteral(e_2, 0L))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "C1_type", true, r_2, 0))) {
			return;
		}
		b.push(r_2);
		b.project(List.<RexNode>of(CalciteRuntime.bigint(b, 0L), b.literal(0)));
		b.aggregate(b.groupKey(CalciteRuntime.bigint(b, 0L), b.literal(0)));
		b.aggregate(b.groupKey(), b.aggregateCall((SqlAggFunction) a_1.getAggregation(), false, null, a_1.getName(), List.<RexNode>of(CalciteRuntime.bigint(b, 0L))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default RewriteDistinctAggregates toRule() {
			return new RewriteDistinctAggregates(this);
		}

		@Override
		default String description() {
			return "RewriteDistinctAggregates";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalProject.class).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
