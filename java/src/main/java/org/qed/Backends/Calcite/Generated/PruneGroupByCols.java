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

public class PruneGroupByCols extends RelRule<PruneGroupByCols.Config> {
	protected PruneGroupByCols(Config config) {
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
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 2)) {
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
		final List<Integer> g_3 = r_1.getGroupSet().asList();
		final RexNode e_4 = CalciteRuntime.ref(g_3.get(0));
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_5 = r_1.getAggCallList().get(0);
		if (!(!a_5.hasFilter() && a_5.getCollation().getFieldCollations().isEmpty() && a_5.isDistinct() == false && a_5.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_6 = CalciteRuntime.ref(a_5.getArgList().get(0));
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 1)) {
			return;
		}
		final AggregateCall a_7 = r_1.getAggCallList().get(1);
		if (!(!a_7.hasFilter() && a_7.getCollation().getFieldCollations().isEmpty() && a_7.isDistinct() == false && a_7.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_8 = CalciteRuntime.ref(a_7.getArgList().get(0));
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 2)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 4)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "K_Type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_2, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_2, 2))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_2, 3))) {
			return;
		}
		b.push(r_2);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0)), b.aggregateCall((SqlAggFunction) a_5.getAggregation(), false, null, a_5.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 1))), b.aggregateCall((SqlAggFunction) a_7.getAggregation(), false, null, a_7.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneGroupByCols toRule() {
			return new PruneGroupByCols(this);
		}

		@Override
		default String description() {
			return "PruneGroupByCols";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalAggregate.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()));
		}
	}
}
