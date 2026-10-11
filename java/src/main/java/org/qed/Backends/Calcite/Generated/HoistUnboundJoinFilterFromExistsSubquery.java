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

public class HoistUnboundJoinFilterFromExistsSubquery extends RelRule<HoistUnboundJoinFilterFromExistsSubquery.Config> {
	protected HoistUnboundJoinFilterFromExistsSubquery(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalFilter r_0 = call.rel(0);
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0 instanceof RexSubQuery && ((RexSubQuery) e_0).getKind() == org.apache.calcite.sql.SqlKind.EXISTS)) {
			return;
		}
		if (!(((RexSubQuery) e_0).rel instanceof LogicalJoin)) {
			return;
		}
		final LogicalJoin r_1000 = (LogicalJoin) ((RexSubQuery) e_0).rel;
		if (!(r_1000.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_1 = r_1000.getCondition();
		final List<RexNode> c_2 = CalciteRuntime.conjunctions(e_1);
		if (!(c_2.size() == 2)) {
			return;
		}
		final RexNode e_3 = c_2.get(0);
		final RexNode[] args_4 = new RexNode[]{};
		final RexNode sym_5 = e_3;
		if (!(CalciteRuntime.abstracts(sym_5, args_4))) {
			return;
		}
		final RexNode e_6 = c_2.get(1);
		final RexNode[] args_7 = new RexNode[]{CalciteRuntime.ref(0), CalciteRuntime.ref(1)};
		final RexNode sym_8 = e_6;
		if (!(CalciteRuntime.abstracts(sym_8, args_7))) {
			return;
		}
		final RelNode r_1001 = (RelNode) r_1000.getLeft();
		if (!(r_1001.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Left_Type", true, r_1001, 0))) {
			return;
		}
		final RelNode r_1002 = (RelNode) r_1000.getRight();
		if (!(r_1002.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Right_Type", true, r_1002, 0))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(CalciteRuntime.bindColumn(types, "Outer_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_1001);
		b.push(r_1002);
		b.join(JoinRelType.INNER, CalciteRuntime.instantiate(sym_8, args_7, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)}));
		final RelNode sub_9 = b.build();
		b.filter(b.and(RexSubQuery.exists(sub_9), CalciteRuntime.instantiate(sym_5, args_4, new RexNode[]{})));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default HoistUnboundJoinFilterFromExistsSubquery toRule() {
			return new HoistUnboundJoinFilterFromExistsSubquery(this);
		}

		@Override
		default String description() {
			return "HoistUnboundJoinFilterFromExistsSubquery";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).predicate(r_1 -> r_1.getRowType().getFieldCount() == 1).anyInputs());
		}
	}
}
