package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class AggregateRemove extends RelRule<AggregateRemove.Config> {
	protected AggregateRemove(Config config) {
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
		default AggregateRemove toRule() {
			return new AggregateRemove(this);
		}

		@Override
		default String description() {
			return "AggregateRemove";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_1 -> s_1.operand(LogicalAggregate.class).predicate(aggregate -> org.apache.calcite.rel.core.Aggregate.isSimple(aggregate) && aggregate.getAggCallList().isEmpty() && aggregate.getGroupSet().cardinality() == aggregate.getInput().getRowType().getFieldCount()).oneInput(s_0 -> s_0.operand(RelNode.class).predicate(input -> Boolean.TRUE.equals(input.getCluster().getMetadataQuery().areColumnsUnique(input, org.apache.calcite.util.ImmutableBitSet.range(input.getRowType().getFieldCount())))).anyInputs());
		}

	}
}
