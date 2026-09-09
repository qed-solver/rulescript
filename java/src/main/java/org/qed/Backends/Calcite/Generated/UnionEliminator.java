package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class UnionEliminator extends RelRule<UnionEliminator.Config> {
	protected UnionEliminator(Config config) {
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
		default UnionEliminator toRule() {
			return new UnionEliminator(this);
		}

		@Override
		default String description() {
			return "UnionEliminator";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_1 -> s_1.operand(LogicalUnion.class).predicate(union -> union.all).inputs(s_0 -> s_0.operand(RelNode.class).anyInputs());
		}

	}
}
