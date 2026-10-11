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

public class ProjectJoinJoinRemove extends RelRule<ProjectJoinJoinRemove.Config> {
	protected ProjectJoinJoinRemove(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		if (!(r_0.getProjects().size() == 2)) {
			return;
		}
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 0)) {
			return;
		}
		final RexNode e_1 = r_0.getProjects().get(1);
		if (!(e_1 instanceof RexInputRef && ((RexInputRef) e_1).getIndex() == 2)) {
			return;
		}
		final LogicalJoin r_1 = call.rel(1);
		if (!(r_1.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_2 = r_1.getCondition();
		if (!(e_2 instanceof RexCall && ((RexCall) e_2).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_2).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_3 = ((RexCall) e_2).getOperands().get(0);
		if (!(e_3 instanceof RexInputRef && ((RexInputRef) e_3).getIndex() == 0)) {
			return;
		}
		final RexNode e_4 = ((RexCall) e_2).getOperands().get(1);
		if (!(e_4 instanceof RexInputRef && ((RexInputRef) e_4).getIndex() == 2)) {
			return;
		}
		final LogicalJoin r_2 = call.rel(2);
		if (!(r_2.getJoinType() == JoinRelType.LEFT)) {
			return;
		}
		final RexNode e_5 = r_2.getCondition();
		if (!(e_5 instanceof RexCall && ((RexCall) e_5).getOperator() == SqlStdOperatorTable.EQUALS && ((RexCall) e_5).getOperands().size() == 2)) {
			return;
		}
		final RexNode e_6 = ((RexCall) e_5).getOperands().get(0);
		if (!(e_6 instanceof RexInputRef && ((RexInputRef) e_6).getIndex() == 0)) {
			return;
		}
		final RexNode e_7 = ((RexCall) e_5).getOperands().get(1);
		if (!(e_7 instanceof RexInputRef && ((RexInputRef) e_7).getIndex() == 1)) {
			return;
		}
		final RelNode r_3 = call.rel(3);
		if (!(r_3.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", false, r_3, 0))) {
			return;
		}
		if (!(CalciteRuntime.isUnique(call, r_3, 0))) {
			return;
		}
		final RelNode r_4 = call.rel(4);
		if (!(r_4.deepEquals(r_3))) {
			return;
		}
		final RelNode r_5 = call.rel(5);
		if (!(r_5.getRowType().getFieldCount() == 1)) {
			return;
		}
		if (!(CalciteRuntime.bindColumn(types, "V", false, r_5, 0))) {
			return;
		}
		b.push(r_3);
		b.push(r_5);
		b.join(JoinRelType.LEFT, b.call(SqlStdOperatorTable.EQUALS, CalciteRuntime.field(b, 2, 0), CalciteRuntime.field(b, 2, 1)));
		b.project(List.<RexNode>of(CalciteRuntime.field(b, 1, 0), CalciteRuntime.field(b, 1, 1)));
		call.transformTo(b.build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ProjectJoinJoinRemove toRule() {
			return new ProjectJoinJoinRemove(this);
		}

		@Override
		default String description() {
			return "ProjectJoinJoinRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).oneInput(s_1 -> s_1.operand(LogicalJoin.class).inputs(s_2 -> s_2.operand(LogicalJoin.class).inputs(s_3 -> s_3.operand(RelNode.class).anyInputs(), s_4 -> s_4.operand(RelNode.class).anyInputs()), s_5 -> s_5.operand(RelNode.class).anyInputs()));
		}
	}
}
