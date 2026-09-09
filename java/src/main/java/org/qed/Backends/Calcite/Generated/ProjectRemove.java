package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class ProjectRemove extends RelRule<ProjectRemove.Config> {
	protected ProjectRemove(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		var var_2 = call.builder();
		call.transformTo(var_2.push(call.rel(1)).build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default ProjectRemove toRule() {
			return new ProjectRemove(this);
		}

		@Override
		default String description() {
			return "ProjectRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_1 -> s_1.operand(LogicalProject.class).predicate(project -> org.apache.calcite.rex.RexUtil.isIdentity(project.getProjects(), project.getInput().getRowType())).oneInput(s_0 -> s_0.operand(RelNode.class).anyInputs());
		}

	}
}
