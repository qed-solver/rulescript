package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RelType;
import org.qed.RRuleInstances.PruneGroupByCols;
import org.qed.RuleBuilder;

public class PruneGroupByColsTest {
    public static void runTest() {
        var rule = new PruneGroupByCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneGroupByCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 4, false);
        var aggregateBuilder = RuleChecks.ruleBuilder(builder);
        var aggregate = aggregateBuilder.scan(table)
                .aggregate(aggregateBuilder.groupKey(aggregateBuilder.field(0)),
                        aggregateBuilder.aggregateCall(aggregate("F"), false, null, "F", aggregateBuilder.field(1)),
                        aggregateBuilder.aggregateCall(aggregate("G"), false, null, "G", aggregateBuilder.field(2)))
                .build();
        var plan = LogicalProject.create(aggregate, java.util.List.of(), java.util.List.of(aggregate.getCluster().getRexBuilder().makeInputRef(aggregate, 0),
                aggregate.getCluster().getRexBuilder().makeInputRef(aggregate, 1),
                aggregate.getCluster().getRexBuilder().makeInputRef(aggregate, 2)), aggregate.getRowType().getFieldNames());

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 4, false);
        var expectedInput = expectedBuilder.scan(expectedTable)
                .project(expectedBuilder.field(0), expectedBuilder.field(1), expectedBuilder.field(2)).build();
        var finalBuilder = RuleChecks.ruleBuilder(expectedBuilder);
        var expected = finalBuilder.push(expectedInput)
                .aggregate(finalBuilder.groupKey(finalBuilder.field(0)),
                        finalBuilder.aggregateCall(aggregate("F"), false, null, "F", finalBuilder.field(1)),
                        finalBuilder.aggregateCall(aggregate("G"), false, null, "G", finalBuilder.field(2)))
                .project(finalBuilder.field(0), finalBuilder.field(1), finalBuilder.field(2))
                .build();
        RuleChecks.rewrites(rule, plan, expected);
        var nearMissBuilder = RuleBuilder.create();
        var fourColumns = RuleChecks.table(nearMissBuilder, 4, false);
        var nearMiss = nearMissBuilder.scan(fourColumns)
                .aggregate(nearMissBuilder.groupKey(nearMissBuilder.field(0)),
                        nearMissBuilder.aggregateCall(aggregate("F"), true, null, "F", nearMissBuilder.field(1)),
                        nearMissBuilder.aggregateCall(aggregate("G"), false, null, "G", nearMissBuilder.field(2)))
                .project(nearMissBuilder.field(0), nearMissBuilder.field(1), nearMissBuilder.field(2))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static SqlAggFunction aggregate(String name) {
        return RuleBuilder.create().genericAggregateOp(name, new RelType.BaseType(SqlTypeName.INTEGER, true));
    }

    public static void main(String[] args) {
        System.out.println("Running PruneGroupByCols test...");
        runTest();
    }
}
