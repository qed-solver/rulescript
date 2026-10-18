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

public class OptimizeUpdateFields extends RelRule<OptimizeUpdateFields.Config> {
	protected OptimizeUpdateFields(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 2)) {
			return;
		}
		final RexNode e_1 = r_0.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 3)) {
			return;
		}
		final LogicalProject r_1 = call.rel(1);
		final RexNode e_2 = r_1.getProjects().get(0);
		if (!(e_2 instanceof RexInputRef && ((RexInputRef) e_2).getIndex() == 0)) {
			return;
		}
		final RexNode e_3 = r_1.getProjects().get(1);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 1)) {
			return;
		}
		final RexNode e_4 = r_1.getProjects().get(2);
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final RexNode e_7 = r_1.getProjects().get(3);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 3)) {
			return;
		}
		final LogicalProject r_2 = call.rel(2);
		final RexNode e_8 = r_2.getProjects().get(0);
		if (!(e_8 instanceof RexInputRef && ((RexInputRef) e_8).getIndex() == 0)) {
			return;
		}
		final RexNode e_9 = r_2.getProjects().get(1);
		if (!(e_9 instanceof RexInputRef && ((RexInputRef) e_9).getIndex() == 1)) {
			return;
		}
		final RexNode e_10 = r_2.getProjects().get(2);
		if (!(e_10 instanceof RexInputRef && ((RexInputRef) e_10).getIndex() == 2)) {
			return;
		}
		final RexNode e_11 = r_2.getProjects().get(3);
		final RexNode[] args_12 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_13 = e_11;
		if (!(CalciteRuntime.abstracts(sym_13, args_12))) {
			return;
		}
		final LogicalProject r_3 = call.rel(3);
		final RexNode e_14 = r_3.getProjects().get(0);
		if (!(e_14 instanceof RexInputRef && ((RexInputRef) e_14).getIndex() == 0)) {
			return;
		}
		final RexNode e_15 = r_3.getProjects().get(1);
		if (!(e_15 instanceof RexInputRef && ((RexInputRef) e_15).getIndex() == 1)) {
			return;
		}
		final RexNode e_16 = r_3.getProjects().get(2);
		final RexNode[] args_17 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_18 = e_16;
		if (!(CalciteRuntime.abstracts(sym_18, args_17))) {
			return;
		}
		final RexNode e_19 = r_3.getProjects().get(3);
		if (!(e_19 instanceof RexInputRef && ((RexInputRef) e_19).getIndex() == 1)) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(CalciteRuntime.bindColumn(types, "A_Type", true, r_4, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "B_Type", true, r_4, 1))) {
			return;
		}
		b.push(r_4);
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)}), CalciteRuntime.field(b, 1, 1)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1), CalciteRuntime.field(b, 1, 2), CalciteRuntime.instantiate(sym_13, args_12, new RexNode[]{CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)})));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 2), CalciteRuntime.field(b, 1, 3)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default OptimizeUpdateFields toRule() {
			return new OptimizeUpdateFields(this);
		}

		@Override
		default String description() {
			return "OptimizeUpdateFields";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 2).oneInput(s_1 -> s_1.operand(LogicalProject.class).predicate(r_1 -> r_1.getProjects().size() == 4).oneInput(s_2 -> s_2.operand(LogicalProject.class).predicate(r_2 -> r_2.getProjects().size() == 4).oneInput(s_3 -> s_3.operand(LogicalProject.class).predicate(r_3 -> r_3.getProjects().size() == 4).oneInput(s_4 -> s_4.operand(RelNode.class).predicate(r_4 -> r_4.getRowType().getFieldCount() == 2).anyInputs()))));
		}
	}
}
