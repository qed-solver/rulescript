package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateZeroCardSelect;
import org.qed.RuleBuilder;

public final class EliminateZeroCardSelectTest {
    private EliminateZeroCardSelectTest() {}

    public static void runTest() {
        var rule = new EliminateZeroCardSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        independentlyBuiltMatch();
        meaningfulNoMatch();
    }

    private static void independentlyBuiltMatch() {
        var builder = configuredBuilder();
        var type = builder.getTypeFactory().builder().add("c0", SqlTypeName.INTEGER).build();
        RelNode empty = LogicalValues.createEmpty(builder.getCluster(), type);
        var rex = new RexBuilder(builder.getTypeFactory());
        var first = LogicalFilter.create(empty, rex.makeLiteral(true));
        var second = LogicalFilter.create(first, rex.makeLiteral(false));
        var expected = LogicalValues.createEmpty(builder.getCluster(), type);
        RuleChecks.rewrites(new EliminateZeroCardSelect(), second, expected);
    }

    private static void meaningfulNoMatch() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        RelNode nonEmptyInput = builder.scan(table).build();
        var condition = builder.getRexBuilder().makeLiteral(false);
        RelNode filtered = LogicalFilter.create(nonEmptyInput, condition);
        RuleChecks.doesNotApply(new EliminateZeroCardSelect(), filtered);
    }

    private static RelBuilder configuredBuilder() {
        return RuleChecks.ruleBuilder(RuleBuilder.create());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateZeroCardSelect test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("EliminateZeroCardSelect test failed");
        }
    }
}
