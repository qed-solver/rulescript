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

public class FoldColumnAccess extends RelRule<FoldColumnAccess.Config> {
	protected FoldColumnAccess(Config config) {
		super(config);
	}

	@Override
	public void onMatch(RelOptRuleCall call) {
		final RelBuilder b = CalciteRuntime.builder(call);
		final Map<String, RelDataType> types = new HashMap<>();
		final LogicalProject r_0 = call.rel(0);
		final RexNode e_0 = r_0.getProjects().get(0);
		if (!(e_0 instanceof RexInputRef && ((RexInputRef) e_0).getIndex() == 1)) {
			return;
		}
		final LogicalProject r_1 = call.rel(1);
		final RexNode e_1 = r_1.getProjects().get(0);
		final RexNode[] args_2 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_3 = e_1;
		if (!(CalciteRuntime.abstracts(sym_3, args_2))) {
			return;
		}
		final RexNode e_4 = r_1.getProjects().get(1);
		final RexNode[] args_5 = new RexNode[]{CalciteRuntime.ref(0)};
		final RexNode sym_6 = e_4;
		if (!(CalciteRuntime.abstracts(sym_6, args_5))) {
			return;
		}
		final RelNode r_2 = call.rel(2);
		if (!(CalciteRuntime.bindColumn(types, "Source_Type", true, r_2, 0))) {
			return;
		}
		b.push(r_2);
		b.project(CalciteRuntime.instantiate(sym_6, args_5, new RexNode[]{CalciteRuntime.field(b, 1, 0)}));
		CalciteRuntime.transform(call, b);
	}

	public interface Config extends EmptyConfig {
		Config DEFAULT = new Config() {};

		@Override
		default FoldColumnAccess toRule() {
			return new FoldColumnAccess(this);
		}

		@Override
		default String description() {
			return "FoldColumnAccess";
		}

		@Override
		default RelRule.OperandTransform operandSupplier() {
			return s_0 -> s_0.operand(LogicalProject.class).predicate(r_0 -> r_0.getProjects().size() == 1).oneInput(s_1 -> s_1.operand(LogicalProject.class).predicate(r_1 -> r_1.getProjects().size() == 2).oneInput(s_2 -> s_2.operand(RelNode.class).predicate(r_2 -> r_2.getRowType().getFieldCount() == 1).anyInputs()));
		}
	}
}
