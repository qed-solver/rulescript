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

public class PullupCorrelatedPredicates extends RelRule<PullupCorrelatedPredicates.Config> {
	protected PullupCorrelatedPredicates(Config config) {
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
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RexNode e_5 = c_1.get(1);
		if (!(e_5 instanceof RexSubQuery && ((RexSubQuery) e_5).getKind() == org.apache.calcite.sql.SqlKind.EXISTS)) {
			return;
		}
		if (!(((RexSubQuery) e_5).rel instanceof LogicalFilter)) {
			return;
		}
		final LogicalFilter r_1000 = (LogicalFilter) ((RexSubQuery) e_5).rel;
		final RexNode e_6 = r_1000.getCondition();
		final List<RexNode> c_7 = CalciteRuntime.conjunctions(e_6);
		if (!(c_7.size() == 2)) {
			return;
		}
		final RexNode e_8 = c_7.get(0);
		final RexNode[] args_9 = new RexNode[]{};
		final RexNode sym_10 = e_8;
		if (!(CalciteRuntime.abstracts(sym_10, args_9))) {
			return;
		}
		final RexNode e_11 = c_7.get(1);
		final RexNode[] args_12 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_13 = e_11;
		if (!(CalciteRuntime.abstracts(sym_13, args_12))) {
			return;
		}
		final RelNode r_1001 = (RelNode) r_1000.getInput();
		if (!(r_1001.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Inner_Type", true, r_1001, 0))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Outer_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_1001);
		b.filter(CalciteRuntime.instantiate(sym_13, args_12, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		final RelNode sub_14 = b.build();
		b.filter(b.and(CalciteRuntime.instantiate(sym_4, args_3, new RexNode[]{CalciteRuntime.field(b, 1, 0)}), b.and(CalciteRuntime.instantiate(sym_10, args_9, new RexNode[]{}), RexSubQuery.exists(sub_14))));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PullupCorrelatedPredicates toRule() {
			return new PullupCorrelatedPredicates(this);
		}

		@Override
		default String description() {
			return "PullupCorrelatedPredicates";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
