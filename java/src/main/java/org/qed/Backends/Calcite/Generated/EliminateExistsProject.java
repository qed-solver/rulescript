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

public class EliminateExistsProject extends RelRule<EliminateExistsProject.Config> {
	protected EliminateExistsProject(Config config) {
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
		if (!(((RexSubQuery) e_0).rel instanceof LogicalProject)) {
			return;
		}
		final LogicalProject r_1000 = (LogicalProject) ((RexSubQuery) e_0).rel;
		if (!(r_1000.getProjects().size() == 1)) {
			return;
		}
		final RexNode e_1 = r_1000.getProjects().get(0);
		final RexNode[] args_2 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_3 = e_1;
		if (!(CalciteRuntime.abstracts(sym_3, args_2))) {
			return;
		}
		final RelNode r_1001 = (RelNode) r_1000.getInput();
		if (!(r_1001.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Input_Type", true, r_1001, 0))) {
			return;
		}
		final RelNode r_1 = call.rel(1);
		if (!(r_1.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "Outer_Type", true, r_1, 0))) {
			return;
		}
		b.push(r_1);
		b.push(r_1001);
		final RelNode sub_4 = b.build();
		b.filter(RexSubQuery.exists(sub_4));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default EliminateExistsProject toRule() {
			return new EliminateExistsProject(this);
		}

		@Override
		default String description() {
			return "EliminateExistsProject";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalFilter.class).oneInput(s_1 -> s_1.operand(RelNode.class).anyInputs());
		}
	}
}
