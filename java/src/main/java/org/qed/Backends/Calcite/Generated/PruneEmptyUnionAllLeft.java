package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class PruneEmptyUnionAllLeft extends RelRule<PruneEmptyUnionAllLeft.Config> {
	protected PruneEmptyUnionAllLeft(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		var var_3 = call.builder();
		call.transformTo(var_3.push(call.rel(2)).build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default PruneEmptyUnionAllLeft toRule() {
			return new PruneEmptyUnionAllLeft(this);
		}

		@Override
		default String description() {
			return "PruneEmptyUnionAllLeft";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_2 -> s_2.operand(LogicalUnion.class).predicate(union -> union.all).inputs(s_0 -> s_0.operand(LogicalValues.class).noInputs(), s_1 -> s_1.operand(RelNode.class).anyInputs());
		}

	}
}
