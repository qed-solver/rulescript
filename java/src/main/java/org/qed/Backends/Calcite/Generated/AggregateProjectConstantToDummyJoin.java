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

public class AggregateProjectConstantToDummyJoin extends RelRule<AggregateProjectConstantToDummyJoin.Config> {
	protected AggregateProjectConstantToDummyJoin(Config config) {
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
		if (!(r_0.getGroupSet().cardinality() == 3)) {
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
		final RexNode e_2 = CalciteRuntime.ref(g_0.get(1));
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 1)) {
			return;
		}
		final RexNode e_3 = CalciteRuntime.ref(g_0.get(2));
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 2)) {
			return;
		}
		final AggregateCall a_4 = r_0.getAggCallList().get(0);
		if (!(!a_4.hasFilter() && a_4.getCollation().getFieldCollations().isEmpty() && a_4.isDistinct() == false && a_4.getArgList().size() == 1)) {
			return;
		}
		final RexNode e_5 = CalciteRuntime.ref(a_4.getArgList().get(0));
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 2)) {
			return;
		}
		final LogicalProject r_1 = call.rel(1);
		final RexNode e_6 = r_1.getProjects().get(0);
		if (!(e_6.isAlwaysTrue())) {
			return;
		}
		final RexNode e_7 = r_1.getProjects().get(1);
		if (!(e_7.isAlwaysFalse())) {
			return;
		}
		final RexNode e_8 = r_1.getProjects().get(2);
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 0)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "S_Type", true, r_2, 0))) {
			return;
		}
		b.push(r_2);
		b.values(new String[]{"x", "d"}, true, false);
		b.join(JoinRelType.INNER, b.literal(true));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 0)));
		b.aggregate(b.groupKey(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2)), b.aggregateCall((SqlAggFunction) a_4.getAggregation(), false, null, a_4.getName(), List.<RexNode>of(CalciteRuntime.field(b, 1, 2))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateProjectConstantToDummyJoin toRule() {
			return new AggregateProjectConstantToDummyJoin(this);
		}

		@Override
		default String description() {
			return "AggregateProjectConstantToDummyJoin";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalProject.class).predicate(r_1 -> r_1.getProjects().size() == 3).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
