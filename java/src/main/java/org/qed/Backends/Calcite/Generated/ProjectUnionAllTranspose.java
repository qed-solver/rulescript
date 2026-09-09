package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class ProjectUnionAllTranspose extends RelRule<ProjectUnionAllTranspose.Config> {
	protected ProjectUnionAllTranspose(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		var var_4 = call.builder();
		call.transformTo(var_4.push(call.rel(2)).project(((LogicalProject) call.rel(0)).getProjects()).push(call.rel(3)).project(((LogicalProject) call.rel(0)).getProjects()).union(true, 2).build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ProjectUnionAllTranspose toRule() {
			return new ProjectUnionAllTranspose(this);
		}

		@Override
		default String description() {
			return "ProjectUnionAllTranspose";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_3 -> s_3.operand(LogicalProject.class).oneInput(s_2 -> s_2.operand(LogicalUnion.class).predicate(union -> union.all).inputs(s_0 -> s_0.operand(RelNode.class).anyInputs(), s_1 -> s_1.operand(RelNode.class).anyInputs()));
		}

	}
}
