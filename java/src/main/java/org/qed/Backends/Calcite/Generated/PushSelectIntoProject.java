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

public class PushSelectIntoProject extends RelRule<PushSelectIntoProject.Config> {
	protected PushSelectIntoProject(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = CalciteRuntime.conjunctions(e_0);
		if (!(c_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RexNode e_5 = c_1.get(1);
		final RexNode[] args_6 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_7 = e_5;
		if (!(CalciteRuntime.abstracts(sym_7, args_6))) {
			return;
		}
		final LogicalProject r_1 = call.rel(1);
		final RexNode e_8 = r_1.getProjects().get(0);
		final RexNode[] args_9 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_10 = e_8;
		if (!(CalciteRuntime.abstracts(sym_10, args_9))) {
			return;
		}
		final RexNode e_11 = r_1.getProjects().get(1);
		if (!(e_11 instanceof RexInputRef && ((RexInputRef) e_11).getIndex() == 0)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_2, 0))) {
			return;
		}
		b.push(r_2);
		b.filter(CalciteRuntime.instantiate(sym_4, args_3, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.project(List.<RexNode>of(CalciteRuntime.instantiate(sym_10, args_9, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.field(b, 1, 0)));
		b.filter(CalciteRuntime.instantiate(sym_7, args_6, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PushSelectIntoProject toRule() {
			return new PushSelectIntoProject(this);
		}

		@Override
		default String description() {
			return "PushSelectIntoProject";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(LogicalProject.class).predicate(r_1 -> r_1.getProjects().size() == 2).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
