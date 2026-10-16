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

public class PushVariantIntoScan extends RelRule<PushVariantIntoScan.Config> {
	protected PushVariantIntoScan(Config config) {
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
		final RexNode[] args_2 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_3 = e_1;
		if (!(CalciteRuntime.abstracts(sym_3, args_2))) {
			return;
		}
		final RexNode e_4 = r_0.getProjects().get(2);
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final RexNode e_7 = r_0.getProjects().get(3);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 1)) {
			return;
		}
		final LogicalFilter r_1 = call.rel(1);
		final RexNode e_8 = r_1.getCondition();
		final RexNode[] args_9 = new RexNode[]{sym_3};
		final RexNode sym_10 = e_8;
		if (!(CalciteRuntime.abstracts(sym_10, args_9))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "C_Type", true, r_2, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Variant_Type", true, r_2, 1))) {
			return;
		}
		b.push(r_2);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.instantiate(sym_3, args_2, new RexNode[]{CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.field(b, 1, 1)));
		b.filter(CalciteRuntime.instantiate(sym_10, args_9, new RexNode[]{CalciteRuntime.field(b, 1, 1)}));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PushVariantIntoScan toRule() {
			return new PushVariantIntoScan(this);
		}

		@Override
		default String description() {
			return "PushVariantIntoScan";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 4).oneInput(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 2).anyInputs()));
		}
	}
}
