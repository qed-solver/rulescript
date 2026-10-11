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

public class TryDecorrelateProjectInnerJoin extends RelRule<TryDecorrelateProjectInnerJoin.Config> {
	protected TryDecorrelateProjectInnerJoin(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		if (!(r_0.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_0 = r_0.getCondition();
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1), CalciteRuntime.ref(2)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "INTEGER", true, r_1, 0))) {
			return;
		}
		final LogicalProject r_2 = call.rel(2);
		if (!(r_2.getProjects().size() == 2)) {
			return;
		}
		final RexNode e_3 = r_2.getProjects().get(0);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RexNode e_4 = r_2.getProjects().get(1);
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final LogicalJoin r_3 = call.rel(3);
		if (!(r_3.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_7 = r_3.getCondition();
		final RexNode[] args_8 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_9 = e_7;
		if (!(CalciteRuntime.abstracts(sym_9, args_8))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "INTEGER", true, r_4, 0))) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(r_5.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "INTEGER", true, r_5, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_4);
		b.push(r_5);
		b.join(JoinRelType.INNER, b.literal(true));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)})));
		b.join(JoinRelType.LEFT, b.and(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1), CalciteRuntime.field(b, 2, 3)}), CalciteRuntime.instantiate(sym_9, args_8, new RexNode[]{CalciteRuntime.field(b, 2, 1), CalciteRuntime.field(b, 2, 2)})));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 3)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default TryDecorrelateProjectInnerJoin toRule() {
			return new TryDecorrelateProjectInnerJoin(this);
		}

		@Override
		default String description() {
			return "TryDecorrelateProjectInnerJoin";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalProject.class).oneInput(s_3 -> s_3.operand(LogicalJoin.class).inputs(s_4 -> s_4.operand(RelNode.class).anyInputs(), s_5 -> s_5.operand(RelNode.class).anyInputs())));
		}
	}
}
