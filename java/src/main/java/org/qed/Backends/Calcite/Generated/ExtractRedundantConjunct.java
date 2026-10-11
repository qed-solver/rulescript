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

public class ExtractRedundantConjunct extends RelRule<ExtractRedundantConjunct.Config> {
	protected ExtractRedundantConjunct(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> d_1 = RelOptUtil.disjunctions(e_0);
		if (!(d_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = d_1.get(0);
		final List<RexNode> c_3 = RelOptUtil.conjunctions(e_2);
		if (!(c_3.size() == 2)) {
			return;
		}
		final RexNode e_4 = c_3.get(0);
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final RexNode e_7 = c_3.get(1);
		final RexNode[] args_8 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_9 = e_7;
		if (!(CalciteRuntime.abstracts(sym_9, args_8))) {
			return;
		}
		final RexNode e_10 = d_1.get(1);
		final List<RexNode> c_11 = RelOptUtil.conjunctions(e_10);
		if (!(c_11.size() == 2)) {
			return;
		}
		final RexNode e_12 = c_11.get(0);
		if (!(sym_6.equals(e_12))) {
			return;
		}
		final RexNode e_13 = c_11.get(1);
		final RexNode[] args_14 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_15 = e_13;
		if (!(CalciteRuntime.abstracts(sym_15, args_14))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.filter(b.and(CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), b.or(CalciteRuntime.instantiate(sym_9, args_8, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), CalciteRuntime.instantiate(sym_15, args_14, new RexNode[]{CalciteRuntime.field(b, 1, 0)}))));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ExtractRedundantConjunct toRule() {
			return new ExtractRedundantConjunct(this);
		}

		@Override
		default String description() {
			return "ExtractRedundantConjunct";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
