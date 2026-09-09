package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class PruneEmptyIntersectLeft extends RelRule<PruneEmptyIntersectLeft.Config> {
	protected PruneEmptyIntersectLeft(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		var var_3 = call.builder();
		call.transformTo(var_3.push(call.rel(2)).empty().build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneEmptyIntersectLeft toRule() {
			return new PruneEmptyIntersectLeft(this);
		}

		@Override
		default String description() {
			return "PruneEmptyIntersectLeft";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_2 -> s_2.operand(LogicalIntersect.class).predicate(intersect -> !intersect.all).inputs(s_0 -> s_0.operand(LogicalValues.class).noInputs(), s_1 -> s_1.operand(RelNode.class).anyInputs());
		}

	}
}
