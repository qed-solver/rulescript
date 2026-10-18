package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.OptimizeExpand;

import java.util.List;

public class OptimizeExpandTest {
    public static void runTest() {
        var rule = new OptimizeExpand();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OptimizeExpand rule) {
        var sourceBuilder = RuleBuilder.create();
        var table = RuleChecks.table(sourceBuilder, 4, false);
        var before = expandAndAggregate(RuleChecks.ruleBuilder(sourceBuilder), scan(sourceBuilder, table), false);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 4, false);
        var expectedRelBuilder = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedSource = scan(expectedRelBuilder, expectedTable);
        var dedup = expectedRelBuilder.push(expectedSource).aggregate(expectedRelBuilder.groupKey(0, 1, 2, 3)).build();
        var expected = expandAndAggregate(expectedRelBuilder, dedup, false);

        RuleChecks.rewrites(rule, before, expected);

        var nearBuilderBase = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearBuilderBase, 4, false);
        var nearBuilder = RuleChecks.ruleBuilder(nearBuilderBase);
        var nearSource = scan(nearBuilder, nearTable);
        var nearMiss = expandAndAggregate(nearBuilder, nearSource, false, false);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode scan(org.apache.calcite.tools.RelBuilder builder, String table) {
        return builder.scan(table).build();
    }

    private static RelNode expandAndAggregate(org.apache.calcite.tools.RelBuilder builder, RelNode source,
                                              boolean reversedBranch) {
        return expandAndAggregate(builder, source, reversedBranch, true);
    }

    private static RelNode expandAndAggregate(org.apache.calcite.tools.RelBuilder builder, RelNode source,
                                              boolean reversedBranch, boolean unionAll) {
        builder.push(source);
        var base = builder.peek();
        var branches = new java.util.ArrayList<RelNode>();
        for (var tag = 0; tag < 3; tag++) {
            builder.push(base);
            var fields = List.<RexNode>of(builder.field(0),
                    builder.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS, builder.field(1), builder.literal(tag)),
                    builder.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS, builder.field(2), builder.literal(tag + 10)),
                    builder.field(3));
            branches.add(builder.project(fields).build());
        }
        if (reversedBranch) {
            java.util.Collections.reverse(branches);
        }
        builder.push(branches.get(0));
        for (var i = 1; i < branches.size(); i++) {
            builder.push(branches.get(i));
        }
        builder.union(unionAll, branches.size());
        builder.aggregate(builder.groupKey(0, 1, 2, 3));
        builder.aggregate(builder.groupKey(0, 1, 2),
                builder.count(false, "Cnt", builder.field(3)));
        return builder.aggregate(builder.groupKey(0),
                builder.aggregateCall(org.qed.RuleBuilder.create().genericAggregateOp("outeragg",
                        org.qed.RexRN.varType("Agg2", true)), false, null, "Agg2", List.of(builder.field(3))))
                .build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
