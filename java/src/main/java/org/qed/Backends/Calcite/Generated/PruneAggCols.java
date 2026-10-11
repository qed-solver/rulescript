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

public class PruneAggCols extends RelRule<PruneAggCols.Config> {
	protected PruneAggCols(Config config) {
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
		final LogicalAggregate r_1 = call.rel(1);
		if (!(r_1.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_1.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_1.getAggCallList().size() == 2)) {
			return;
		}
		final List<Integer> g_2 = r_1.getGroupSet().asList();
		final RexNode e_3 = CalciteRuntime.ref(g_2.get(0));
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_4 = r_1.getAggCallList().get(0);
		if (!(!a_4.hasFilter() && a_4.getCollation().getFieldCollations().isEmpty() && a_4.isDistinct() == false && a_4.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_5 = CalciteRuntime.ref(a_4.getArgList().get(0));
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 1)) {
			return;
		}
		final AggregateCall a_6 = r_1.getAggCallList().get(1);
		if (!(!a_6.hasFilter() && a_6.getCollation().getFieldCollations().isEmpty() && a_6.isDistinct() == false && a_6.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_7 = CalciteRuntime.ref(a_6.getArgList().get(0));
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 2)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "K_Type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_2, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_2, 2))) {
			return;
		}
		b.push(r_2);
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_4.getAggregation(), false, null, a_4.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneAggCols toRule() {
			return new PruneAggCols(this);
		}

		@Override
		default String description() {
			return "PruneAggCols";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 2).oneInput(s_1 -> s_1.operand(LogicalAggregate.class).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 3).anyInputs()));
		}
	}
}
