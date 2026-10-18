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

public class CombineUnions extends RelRule<CombineUnions.Config> {
	protected CombineUnions(Config config) {
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
		if (!(r_0.getAggCallList().size() == 0)) {
			return;
		}
		final List<Integer> g_0 = r_0.getGroupSet().asList();
		final RexNode e_1 = CalciteRuntime.ref(g_0.get(0));
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final LogicalUnion r_1 = call.rel(1);
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_2, 0))) {
			return;
		}
		final LogicalAggregate r_3 = call.rel(3);
		if (!(r_3.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_3.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_3.getAggCallList().size() == 0)) {
			return;
		}
		final List<Integer> g_2 = r_3.getGroupSet().asList();
		final RexNode e_3 = CalciteRuntime.ref(g_2.get(0));
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final LogicalUnion r_4 = call.rel(4);
		final RelNode r_5 = call.rel(5);
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_5, 0))) {
			return;
		}
		final RelNode r_6 = call.rel(6);
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_6, 0))) {
			return;
		}
		final RelNode r_7 = call.rel(7);
		if (!(CalciteRuntime.bindColumn(types, "T", true, r_7, 0))) {
			return;
		}
		b.push(r_2);
		b.push(r_5);
		b.push(r_6);
		b.push(r_7);
		b.union(true, 4);
		b.aggregate(CalciteRuntime.groupKey(b, CalciteRuntime.field(b, 1, 0)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default CombineUnions toRule() {
			return new CombineUnions(this);
		}

		@Override
		default String description() {
			return "CombineUnions";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalAggregate.class).oneInput(s_1 -> s_1.operand(LogicalUnion.class).predicate(r_1 -> r_1.all == true && r_1.getInputs().size() == 3).inputs(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1).anyInputs(), s_3 -> s_3.operand(LogicalAggregate.class).oneInput(s_4 -> s_4.operand(LogicalUnion.class).predicate(r_4 -> r_4.all == true && r_4.getInputs().size() == 2).inputs(s_5 -> s_5.operand(RelNode.class).predicate(r_5 -> r_5.getRowType().getFieldCount() == 1).anyInputs(), s_6 -> s_6.operand(RelNode.class).predicate(r_6 -> r_6.getRowType().getFieldCount() == 1).anyInputs())), s_7 -> s_7.operand(RelNode.class).predicate(r_7 -> r_7.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
