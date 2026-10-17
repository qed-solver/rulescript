package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RelType;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.PushLeakproofProjectionsIntoPermeableBarrier;

import java.util.List;

public class PushLeakproofProjectionsIntoPermeableBarrierTest {
    public static void runTest() {
        var rule = new PushLeakproofProjectionsIntoPermeableBarrier();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushLeakproofProjectionsIntoPermeableBarrier rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var expr = builder.genericProjectionOp("Expr", new RelType.VarType("Expr_Type", true));
        var input = builder.scan(table).build();
        var inputField = builder.getRexBuilder().makeInputRef(input, 0);
        var barrier = LogicalProject.create(input, List.of(), List.of(inputField), List.of("barrier_value"));
        var barrierField = builder.getRexBuilder().makeInputRef(barrier, 0);
        var plan = LogicalProject.create(barrier, List.of(),
                List.of(builder.call(expr, barrierField), barrierField), List.of("value", "passthrough"));

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(table)
                .project(List.of(plain.call(expr, plain.field(0)), plain.field(0)), List.of())
                .project(List.of(plain.field(0), plain.field(1)), List.of()).build();
        RuleChecks.rewrites(rule, plan, expected);

        var nearBuilder = RuleBuilder.create();
        var wideTable = RuleChecks.table(nearBuilder, 2, false);
        var wideExpr = nearBuilder.genericProjectionOp("Expr", new RelType.VarType("Expr_Type", true));
        var wideInput = nearBuilder.scan(wideTable).build();
        var wideField = nearBuilder.getRexBuilder().makeInputRef(wideInput, 0);
        var narrow = LogicalProject.create(wideInput, List.of(), List.of(wideField), List.of("narrow_value"));
        var narrowField = nearBuilder.getRexBuilder().makeInputRef(narrow, 0);
        var nearMiss = LogicalProject.create(narrow, List.of(),
                List.of(nearBuilder.call(wideExpr, narrowField), narrowField), List.of("value", "passthrough"));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofProjectionsIntoPermeableBarrier test...");
        runTest();
    }
}
