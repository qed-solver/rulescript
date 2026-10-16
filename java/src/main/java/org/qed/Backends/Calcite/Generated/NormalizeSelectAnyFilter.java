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

public class NormalizeSelectAnyFilter extends RelRule<NormalizeSelectAnyFilter.Config> {
	protected NormalizeSelectAnyFilter(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		if (!(r_0.getVariablesSet().size() == 0)) {
			return;
		}
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexSubQuery && ((RexSubQuery) e_0).getKind() == org.apache.calcite.sql.SqlKind.IN && ((RexSubQuery) e_0).getOperands().size() == 1)) {
			return;
		}
		final RexNode e_1 = ((RexSubQuery) e_0).getOperands().get(0);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 0)) {
			return;
		}
		final RelNode r_1000 = (RelNode) ((RexSubQuery) e_0).rel;
		if (!(r_1000.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1000, 0))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		final org.apache.calcite.rel.core.CorrelationId cor_2 = b.getCluster().createCorrel();
		final RexNode cv_2 = b.getRexBuilder().makeCorrel(b.peek().getRowType(), cor_2);
		b.push(r_1000);
		final RexNode cond_4 = b.call(SqlStdOperatorTable.EQUALS, b.getRexBuilder().makeFieldAccess(cv_2, 0), CalciteRuntime.field(b, 1, 0));
		b.push(LogicalFilter.create(b.build(), cond_4));
		final RelNode sub_5 = b.build();
		final RexNode cond_3 = RexSubQuery.exists(sub_5);
		b.push(LogicalFilter.create(b.build(), cond_3, com.google.common.collect.ImmutableSet.of(cor_2)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default NormalizeSelectAnyFilter toRule() {
			return new NormalizeSelectAnyFilter(this);
		}

		@Override
		default String description() {
			return "NormalizeSelectAnyFilter";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
