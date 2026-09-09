package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class UnionFilterToFilter extends RelRule<UnionFilterToFilter.Config> {
	protected UnionFilterToFilter(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		if (call.rel(4) != call.rel(2)) return;
		var var_5 = call.builder();
		var var_6 = var_5.push(call.rel(2)).filter(var_5.push(call.rel(2)).or(((LogicalFilter) call.rel(1)).getCondition(), ((LogicalFilter) call.rel(3)).getCondition())).build();
		call.transformTo(var_5.push(var_6).aggregate(var_5.groupKey(var_5.fields())).build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default UnionFilterToFilter toRule() {
			return new UnionFilterToFilter(this);
		}

		@Override
		default String description() {
			return "UnionFilterToFilter";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_4 -> s_4.operand(LogicalUnion.class).predicate(union -> !union.all).inputs(s_1 -> s_1.operand(LogicalFilter.class).oneInput(s_0 -> s_0.operand(RelNode.class).anyInputs()), s_3 -> s_3.operand(LogicalFilter.class).oneInput(s_2 -> s_2.operand(RelNode.class).anyInputs()));
		}

	}
}
