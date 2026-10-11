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

public class PushAggDistinctIntoGroupBy extends RelRule<PushAggDistinctIntoGroupBy.Config> {
	protected PushAggDistinctIntoGroupBy(Config config) {
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
		if (!(r_0.getGroupSet().cardinality() == 2)) {
			return;
		}
		if (!(r_0.getAggCallList().size() == 1)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final RexNode e_1 = r_1.getProjects().get(g_0.get(0));
		final RexNode[] args_2 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_3 = e_1;
		if (!(CalciteRuntime.abstracts(sym_3, args_2))) {
			return;
		}
		final RexNode e_4 = r_1.getProjects().get(g_0.get(1));
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final AggregateCall a_7 = r_0.getAggCallList().get(0);
		if (!(!a_7.hasFilter() && a_7.getCollation().getFieldCollations().isEmpty() && a_7.isDistinct() == true && a_7.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_8 = r_1.getProjects().get(a_7.getArgList().get(0));
		final RexNode[] args_9 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_10 = e_8;
		if (!(CalciteRuntime.abstracts(sym_10, args_9))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "S_Type", true, r_2, 0))) {
			return;
		}
		b.push(r_2);
		b.aggregate(b.groupKey(CalciteRuntime.instantiate(sym_3, args_2, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.instantiate(sym_10, args_9, new RexNode[]{CalciteRuntime.field(b, 1, 0)})));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)), b.aggregateCall((SqlAggFunction) a_7.getAggregation(), false, null, a_7.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PushAggDistinctIntoGroupBy toRule() {
			return new PushAggDistinctIntoGroupBy(this);
		}

		@Override
		default String description() {
			return "PushAggDistinctIntoGroupBy";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalProject.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()));
		}
	}
}
