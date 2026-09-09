package org.qed.Backends.Calcite.Generated;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.qed.Backends.Calcite.EmptyConfig;

public class AggregateUnionAggregateFirst extends RelRule<AggregateUnionAggregateFirst.Config> {
	protected AggregateUnionAggregateFirst(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		var var_5 = call.builder();
		var var_6 = var_5.push(call.rel(3)).push(call.rel(4)).union(true, 2).build();
		call.transformTo(var_5.push(var_6).aggregate(var_5.groupKey(var_5.fields())).build());
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default AggregateUnionAggregateFirst toRule() {
			return new AggregateUnionAggregateFirst(this);
		}

		@Override
		default String description() {
			return "AggregateUnionAggregateFirst";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_4 -> s_4.operand(LogicalAggregate.class).predicate(aggregate -> org.apache.calcite.rel.core.Aggregate.isSimple(aggregate) && aggregate.getAggCallList().isEmpty() && aggregate.getGroupSet().cardinality() == aggregate.getInput().getRowType().getFieldCount()).oneInput(s_3 -> s_3.operand(LogicalUnion.class).predicate(union -> union.all).inputs(s_1 -> s_1.operand(LogicalAggregate.class).predicate(aggregate -> org.apache.calcite.rel.core.Aggregate.isSimple(aggregate) && aggregate.getAggCallList().isEmpty() && aggregate.getGroupSet().cardinality() == aggregate.getInput().getRowType().getFieldCount()).oneInput(s_0 -> s_0.operand(RelNode.class).anyInputs()), s_2 -> s_2.operand(RelNode.class).anyInputs()));
		}

	}
}
