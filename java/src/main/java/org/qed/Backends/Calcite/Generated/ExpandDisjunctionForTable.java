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

public class ExpandDisjunctionForTable extends RelRule<ExpandDisjunctionForTable.Config> {
	protected ExpandDisjunctionForTable(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		if (!(r_0.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> c_1 = RelOptUtil.conjunctions(e_0);
		if (!(c_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = c_1.get(0);
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RexNode e_5 = c_1.get(1);
		final List<RexNode> d_6 = RelOptUtil.disjunctions(e_5);
		if (!(d_6.size() == 2)) {
			return;
		}
		final RexNode e_7 = d_6.get(0);
		final List<RexNode> c_8 = RelOptUtil.conjunctions(e_7);
		if (!(c_8.size() == 2)) {
			return;
		}
		final RexNode e_9 = c_8.get(0);
		final RexNode[] args_10 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_11 = e_9;
		if (!(CalciteRuntime.abstracts(sym_11, args_10))) {
			return;
		}
		final RexNode e_12 = c_8.get(1);
		final RexNode[] args_13 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_14 = e_12;
		if (!(CalciteRuntime.abstracts(sym_14, args_13))) {
			return;
		}
		final RexNode e_15 = d_6.get(1);
		final List<RexNode> c_16 = RelOptUtil.conjunctions(e_15);
		if (!(c_16.size() == 2)) {
			return;
		}
		final RexNode e_17 = c_16.get(0);
		final RexNode[] args_18 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_19 = e_17;
		if (!(CalciteRuntime.abstracts(sym_19, args_18))) {
			return;
		}
		final RexNode e_20 = c_16.get(1);
		final RexNode[] args_21 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_22 = e_20;
		if (!(CalciteRuntime.abstracts(sym_22, args_21))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T1_Type", true, r_1, 0))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "T2_Type", true, r_2, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_2);
		b.join(JoinRelType.INNER, b.and(b.and(CalciteRuntime.instantiate(sym_4, args_3, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)}), b.or(b.and(CalciteRuntime.instantiate(sym_11, args_10, new RexNode[]{CalciteRuntime.field(b, 2, 0)}), CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 2, 1)})), b.and(CalciteRuntime.instantiate(sym_19, args_18, new RexNode[]{CalciteRuntime.field(b, 2, 0)}), CalciteRuntime.instantiate(sym_22, args_21, new RexNode[]{CalciteRuntime.field(b, 2, 1)})))), b.or(CalciteRuntime.instantiate(sym_11, args_10, new RexNode[]{CalciteRuntime.field(b, 2, 0)}), CalciteRuntime.instantiate(sym_19, args_18, new RexNode[]{CalciteRuntime.field(b, 2, 0)})), b.or(CalciteRuntime.instantiate(sym_14, args_13, new RexNode[]{CalciteRuntime.field(b, 2, 1)}), CalciteRuntime.instantiate(sym_22, args_21, new RexNode[]{CalciteRuntime.field(b, 2, 1)}))));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ExpandDisjunctionForTable toRule() {
			return new ExpandDisjunctionForTable(this);
		}

		@Override
		default String description() {
			return "ExpandDisjunctionForTable";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(RelNode.class).anyInputs());
		}
	}
}
