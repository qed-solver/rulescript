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

public class RewritePredicateSubquery extends RelRule<RewritePredicateSubquery.Config> {
	protected RewritePredicateSubquery(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		if (!(r_0.getVariablesSet().size() == 1)) {
			return;
		}
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexSubQuery && ((RexSubQuery) e_0).getKind() == org.apache.calcite.sql.SqlKind.EXISTS && ((RexSubQuery) e_0).getOperands().size() == 0)) {
			return;
		}
		if (!(((RexSubQuery) e_0).rel instanceof LogicalFilter)) {
			return;
		}
		final LogicalFilter r_1000 = (LogicalFilter) ((RexSubQuery) e_0).rel;
		if (!(r_1000.getVariablesSet().size() == 0)) {
			return;
		}
		final RexNode e_1 = r_1000.getCondition();
		if (!(e_1 instanceof RexCall && ((RexCall) e_1).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_1).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_2 = ((RexCall) e_1).getOperands().get(0);
		if (!(e_2 instanceof RexFieldAccess && ((RexFieldAccess) e_2).getReferenceExpr() instanceof RexCorrelVariable && ((RexFieldAccess) e_2).getField().getIndex() == 0)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_1).getOperands().get(1);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RelNode r_1001 = (RelNode) r_1000.getInput();
		if (!(r_1001.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "K_type", true, r_1001, 0))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "K_type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_1001);
		b.join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default RewritePredicateSubquery toRule() {
			return new RewritePredicateSubquery(this);
		}

		@Override
		default String description() {
			return "RewritePredicateSubquery";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
