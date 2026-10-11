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

public class PruneUnionAllCols extends RelRule<PruneUnionAllCols.Config> {
	protected PruneUnionAllCols(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		if (!(r_0.getProjects().size() == 1)) {
			return;
		}
		final RexNode e_0 = r_0.getProjects().get(0);
		final RexNode[] args_1 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(2)};
		final RexNode sym_2 = e_0;
		if (!(CalciteRuntime.abstracts(sym_2, args_1))) {
			return;
		}
		final LogicalUnion r_1 = call.rel(1);
		if (!(r_1.all == true)) {
			return;
		}
		if (!(r_1.getInputs().size() == 2)) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 3)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C0_Type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C1_Type", true, r_2, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C2_Type", true, r_2, 2))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 3)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C0_Type", true, r_3, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C1_Type", true, r_3, 1))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "C2_Type", true, r_3, 2))) {
			return;
		}
		b.push(r_2);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 2)));
		b.push(r_3);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 2)));
		b.union(true, 2);
		b.project(CalciteRuntime.instantiate(sym_2, args_1, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneUnionAllCols toRule() {
			return new PruneUnionAllCols(this);
		}

		@Override
		default String description() {
			return "PruneUnionAllCols";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalUnion.class).inputs(s_2 -> s_2.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(RelNode.class).anyInputs()));
		}
	}
}
