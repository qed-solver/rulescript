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

public class ProjectJoinTranspose extends RelRule<ProjectJoinTranspose.Config> {
	protected ProjectJoinTranspose(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		if (!(r_0.getProjects().size() == 1)) {
			return;
		}
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexCall && ((RexCall) e_0).getOperands().size() == 2)) {
			return;
		}
		final SqlOperator op_1 = ((RexCall) e_0).getOperator();
		final RexNode e_2 = ((RexCall) e_0).getOperands().get(0);
		final RexNode[] args_3 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_4 = e_2;
		if (!(CalciteRuntime.abstracts(sym_4, args_3))) {
			return;
		}
		final RexNode e_5 = ((RexCall) e_0).getOperands().get(1);
		final RexNode[] args_6 = new RexNode[]{CalciteRuntime.ref(1)};
		final RexNode sym_7 = e_5;
		if (!(CalciteRuntime.abstracts(sym_7, args_6))) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		if (!(r_1.getJoinType() == JoinRelType.INNER)) {
			return;
		}
		final RexNode e_8 = r_1.getCondition();
		final RexNode[] args_9 = new RexNode[]{sym_4, sym_7};
		final RexNode sym_10 = e_8;
		if (!(CalciteRuntime.abstracts(sym_10, args_9))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(r_2.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "L_Type", true, r_2, 0))) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "R_Type", true, r_3, 0))) {
			return;
		}
		b.push(r_2);
		b.project(CalciteRuntime.instantiate(sym_4, args_3, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.push(r_3);
		b.project(CalciteRuntime.instantiate(sym_7, args_6, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		b.join(JoinRelType.INNER, CalciteRuntime.instantiate(sym_10, args_9, new RexNode[]{CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)}));
		b.project(b.call(op_1, CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ProjectJoinTranspose toRule() {
			return new ProjectJoinTranspose(this);
		}

		@Override
		default String description() {
			return "ProjectJoinTranspose";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).inputs(s_2 -> s_2.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(RelNode.class).anyInputs()));
		}
	}
}
