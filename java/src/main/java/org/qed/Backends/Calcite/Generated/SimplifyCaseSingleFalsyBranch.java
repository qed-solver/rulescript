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

public class SimplifyCaseSingleFalsyBranch extends RelRule<SimplifyCaseSingleFalsyBranch.Config> {
	protected SimplifyCaseSingleFalsyBranch(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		final List<RexNode> d_1 = CalciteRuntime.disjunctions(e_0);
		if (!(d_1.size() == 2)) {
			return;
		}
		final RexNode e_2 = d_1.get(0);
		final List<RexNode> c_3 = CalciteRuntime.conjunctions(e_2);
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
		if (!(e_7.isAlwaysFalse())) {
			return;
		}
		final RexNode e_8 = d_1.get(1);
		final List<RexNode> c_9 = CalciteRuntime.conjunctions(e_8);
		if (!(c_9.size() == 2)) {
			return;
		}
		final RexNode e_10 = c_9.get(0);
		if (!(e_10.getKind() == org.apache.calcite.sql.SqlKind.NOT)) {
			return;
		}
		final RexNode e_11 = ((RexCall) e_10).getOperands().get(0);
		if (!(sym_6.equals(e_11))) {
			return;
		}
		final RexNode e_12 = c_9.get(1);
		if (!(RexUtil.isNullLiteral(e_12, true) && e_12.getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName.BOOLEAN)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.filter(b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default SimplifyCaseSingleFalsyBranch toRule() {
			return new SimplifyCaseSingleFalsyBranch(this);
		}

		@Override
		default String description() {
			return "SimplifyCaseSingleFalsyBranch";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
