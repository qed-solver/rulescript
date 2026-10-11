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

public class EliminateExistsGroupBy extends RelRule<EliminateExistsGroupBy.Config> {
	protected EliminateExistsGroupBy(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalJoin r_0 = call.rel(0);
		if (!(r_0.getJoinType() == JoinRelType.SEMI)) {
			return;
		}
		final RexNode e_0 = r_0.getCondition();
		if (!(e_0.isAlwaysTrue())) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Outer_Type", true, r_1, 0))) {
			return;
		}
		final LogicalAggregate r_2 = call.rel(2);
		final LogicalProject r_3 = call.rel(3);
		if (!(r_2.getGroupType() == Aggregate.Group.SIMPLE)) {
			return;
		}
		if (!(r_2.getGroupSet().cardinality() == 1)) {
			return;
		}
		if (!(r_2.getAggCallList().size() == 0)) {
			return;
		}
		final List<Integer> g_1 = r_2.getGroupSet().asList();
		final RexNode e_2 = r_3.getProjects().get(g_1.get(0));
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Input_Type", true, r_4, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_4);
		b.join(JoinRelType.SEMI, b.literal(true));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default EliminateExistsGroupBy toRule() {
			return new EliminateExistsGroupBy(this);
		}

		@Override
		default String description() {
			return "EliminateExistsGroupBy";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalJoin.class).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(LogicalAggregate.class).oneInput(s_3 -> s_3.operand(LogicalProject.class).oneInput(s_4 -> s_4.operand(RelNode.class).anyInputs())));
		}
	}
}
