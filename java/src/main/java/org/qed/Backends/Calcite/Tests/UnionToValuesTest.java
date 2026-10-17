package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalValues;
import org.qed.RRuleInstances.UnionToValues;
import org.qed.RuleBuilder;

import java.math.BigDecimal;
import java.util.List;

public final class UnionToValuesTest {
    public static void runTest() {
        var rule = new UnionToValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var cluster = org.apache.calcite.plan.RelOptCluster.create(
                new org.apache.calcite.plan.hep.HepPlanner(new org.apache.calcite.plan.hep.HepProgramBuilder().build()),
                builder.getRexBuilder());
        var typeFactory = builder.getTypeFactory();
        var types = typeFactory.builder().add("c0", typeFactory.createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER))
                .add("c1", typeFactory.createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER)).build();
        var left = values(cluster, types, new String[][]{{"1", "2"}, {"3", "4"}});
        var middle = values(cluster, types, new String[][]{{"3", "4"}, {"5", "6"}});
        var right = values(cluster, types, new String[][]{{"7", "8"}});
        RelNode union = org.apache.calcite.rel.logical.LogicalUnion.create(List.of(left, middle, right), true);
        RelNode expected = values(cluster, union.getRowType(),
                new String[][]{{"1", "2"}, {"3", "4"}, {"3", "4"}, {"5", "6"}, {"7", "8"}});
        RuleChecks.rewrites(rule, union, expected);

        RelNode nonValues = org.apache.calcite.rel.logical.LogicalValues.create(cluster, types,
                com.google.common.collect.ImmutableList.of());
        RuleChecks.doesNotApply(rule, org.apache.calcite.rel.logical.LogicalUnion.create(
                List.of(left, middle, nonValues), true));
    }

    private static LogicalValues values(org.apache.calcite.plan.RelOptCluster cluster,
                                        org.apache.calcite.rel.type.RelDataType rowType, String[][] rows) {
        var rexBuilder = cluster.getRexBuilder();
        var tuples = new java.util.ArrayList<com.google.common.collect.ImmutableList<org.apache.calcite.rex.RexLiteral>>();
        for (var row : rows) {
            tuples.add(com.google.common.collect.ImmutableList.of(
                    rexBuilder.makeExactLiteral(new BigDecimal(row[0])),
                    rexBuilder.makeExactLiteral(new BigDecimal(row[1]))));
        }
        return LogicalValues.create(cluster, rowType, com.google.common.collect.ImmutableList.copyOf(tuples));
    }

    public static void main(String[] args) {
        System.out.println("Running UnionToValues test...");
        runTest();
    }
}
