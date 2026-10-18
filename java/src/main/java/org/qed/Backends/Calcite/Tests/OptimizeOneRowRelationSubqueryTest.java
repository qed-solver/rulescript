package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.OptimizeOneRowRelationSubquery;
import org.qed.RuleBuilder;

import java.util.List;

public final class OptimizeOneRowRelationSubqueryTest {
    public static void runTest() {
        var rule = new OptimizeOneRowRelationSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OptimizeOneRowRelationSubquery rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var scan = builder.scan(outer).build();
        var b = RuleBuilder.create();
        var values = b.values(new String[]{"d"}, true).build();
        b.push(scan);
        b.push(values);
        b.join(JoinRelType.INNER, b.literal(true));
        var joined = b.build();
        RelNode nearMiss = LogicalProject.create(joined,
                List.of(), List.of(joined.getCluster().getRexBuilder().makeInputRef(joined, 0)),
                List.of("outer"));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeOneRowRelationSubquery test...");
        runTest();
    }
}
