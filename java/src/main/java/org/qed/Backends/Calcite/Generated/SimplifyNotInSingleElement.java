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

public class SimplifyNotInSingleElement extends RelRule<SimplifyNotInSingleElement.Config> {
	protected SimplifyNotInSingleElement(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0.getKind() == org.apache.calcite.sql.SqlKind.NOT)) {
			return;
		}
		final RexNode e_1 = ((RexCall) e_0).getOperands().get(0);
		final List<RexNode> d_2 = CalciteRuntime.disjunctions(e_1);
		if (!(d_2.size() == 1)) {
			return;
		}
		final RexNode e_3 = d_2.get(0);
		if (!(e_3 instanceof RexCall && ((RexCall) e_3).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_3).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_3).getOperands().get(0);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 0)) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_3).getOperands().get(1);
		if (!(e_5 instanceof RexInputRef && ((RexInputRef) e_5).getIndex() == 1)) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1, 0))) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", true, r_1, 1))) {
			return;
		}
		b.push(r_1);
		b.filter(b.call(SqlStdOperatorTable.NOT_EQUALS, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default SimplifyNotInSingleElement toRule() {
			return new SimplifyNotInSingleElement(this);
		}

		@Override
		default String description() {
			return "SimplifyNotInSingleElement";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 2).anyInputs());
		}
	}
}
