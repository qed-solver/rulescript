package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class JoinRightUnionTranspose extends RelRule<JoinRightUnionTranspose.Config> {
	protected JoinRightUnionTranspose(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		var var_5 = call.builder();
		call.transformTo(var_5.push(call.rel(1)).push(call.rel(3)).join(JoinRelType.INNER, ((LogicalJoin) call.rel(0)).getCondition()).push(call.rel(1)).push(call.rel(4)).join(JoinRelType.INNER, ((LogicalJoin) call.rel(0)).getCondition()).union(true, 2).build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default JoinRightUnionTranspose toRule() {
			return new JoinRightUnionTranspose(this);
		}

		@Override
		default String description() {
			return "JoinRightUnionTranspose";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_4 -> s_4.operand(LogicalJoin.class).predicate(join -> join.getJoinType() == JoinRelType.INNER).inputs(s_0 -> s_0.operand(RelNode.class).anyInputs(), s_3 -> s_3.operand(LogicalUnion.class).predicate(union -> union.all).inputs(s_1 -> s_1.operand(RelNode.class).anyInputs(), s_2 -> s_2.operand(RelNode.class).anyInputs()));
		}

	}
}
