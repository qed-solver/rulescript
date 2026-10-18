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

public class ProjectAggregateMerge extends RelRule<ProjectAggregateMerge.Config> {
	protected ProjectAggregateMerge(Config config) {
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
		final RexNode e_2 = r_0.getProjects().get(2);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 3)) {
			return;
		}
		final LogicalAggregate r_1 = call.rel(1);
		final LogicalProject r_2 = call.rel(2);
		if (!(r_1.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_1.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_1.getAggCallList().size() == 3)) {
			return;
		}
		final List<Integer> g_3 = r_1.getGroupSet().asList();
		final RexNode e_4 = r_2.getProjects().get(g_3.get(0));
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final AggregateCall a_7 = r_1.getAggCallList().get(0);
		if (!(!a_7.hasFilter() && a_7.getCollation().getFieldCollations().isEmpty() && a_7.isDistinct() == false && a_7.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_8 = r_2.getProjects().get(a_7.getArgList().get(0));
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_9 = r_1.getAggCallList().get(1);
		if (!(!a_9.hasFilter() && a_9.getCollation().getFieldCollations().isEmpty() && a_9.isDistinct() == false && a_9.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_10 = r_2.getProjects().get(a_9.getArgList().get(0));
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 0)) {
			return;
		}
		final AggregateCall a_11 = r_1.getAggCallList().get(2);
		if (!(!a_11.hasFilter() && a_11.getCollation().getFieldCollations().isEmpty() && a_11.isDistinct() == false && a_11.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_12 = r_2.getProjects().get(a_11.getArgList().get(0));
		if (!(e_12 instanceof RexInputRef && ((RexInputRef) e_12).getIndex() == 0)) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(CalciteRuntime.bindColumn(types, "S_Type", true, r_3, 0))) {
			return;
		}
		b.push(r_3);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0)})), b.aggregateCall((SqlAggFunction) a_7.getAggregation(), false, null, a_7.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 0))), b.aggregateCall((SqlAggFunction) a_11.getAggregation(), false, null, a_11.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 0))));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ProjectAggregateMerge toRule() {
			return new ProjectAggregateMerge(this);
		}

		@Override
		default String description() {
			return "ProjectAggregateMerge";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 3).oneInput(s_1 -> s_1.operand(LogicalAggregate.class).oneInput(s_2 -> s_2.operand(LogicalProject.class).oneInput(s_3 -> s_3.operand(RelNode.class).predicate(r_3 -> r_3.getRowType().getFieldCount() == 1).anyInputs())));
		}
	}
}
