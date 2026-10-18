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

public class AggregateExpandDistinctAggregates extends RelRule<AggregateExpandDistinctAggregates.Config> {
	protected AggregateExpandDistinctAggregates(Config config) {
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
		if (!(r_0.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_0.getAggCallList().size() == 2)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final RexNode e_1 = r_1.getProjects().get(g_0.get(0));
		final RexNode[] args_2 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_3 = e_1;
		if (!(CalciteRuntime.abstracts(sym_3, args_2))) {
			return;
		}
		final AggregateCall a_4 = r_0.getAggCallList().get(0);
		if (!(!a_4.hasFilter() && a_4.getCollation().getFieldCollations().isEmpty() && a_4.isDistinct() == true && a_4.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_5 = r_1.getProjects().get(a_4.getArgList().get(0));
		final RexNode[] args_6 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_7 = e_5;
		if (!(CalciteRuntime.abstracts(sym_7, args_6))) {
			return;
		}
		final AggregateCall a_8 = r_0.getAggCallList().get(1);
		if (!(!a_8.hasFilter() && a_8.getCollation().getFieldCollations().isEmpty() && a_8.isDistinct() == true && a_8.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_9 = r_1.getProjects().get(a_8.getArgList().get(0));
		if (!(sym_7.equals(e_9))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "S_Type", true, r_2, 0))) {
			return;
		}
		b.push(r_2);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.instantiate(sym_3, args_2, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.instantiate(sym_7, args_6, new RexNode[]{CalciteRuntime.field(b, 1, 0)})));
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_4.getAggregation(), false, null, a_4.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))), b.aggregateCall((SqlAggFunction) a_8.getAggregation(), false, null, a_8.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateExpandDistinctAggregates toRule() {
			return new AggregateExpandDistinctAggregates(this);
		}

		@Override
		default String description() {
			return "AggregateExpandDistinctAggregates";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalProject.class).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
