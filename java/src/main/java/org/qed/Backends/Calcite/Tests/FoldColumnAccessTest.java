package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRuleInstances.FoldColumnAccess;
import org.qed.RuleBuilder;

import java.util.List;

public final class FoldColumnAccessTest {
    public static void runTest() {
        var rule = new FoldColumnAccess();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltFieldAccess();
        rejectsDifferentFieldAccess();
    }

    private static void rewritesHandBuiltFieldAccess() {
        var rule = new FoldColumnAccess();
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scan = builder.scan(table).build();
        var input = builder.getRexBuilder().makeInputRef(scan, 0);
        var offset = builder.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS, input, builder.literal(1));
        var tuple = LogicalProject.create(scan, List.of(), List.of(input, offset), List.of("first", "second"));
        var field = builder.getRexBuilder().makeInputRef(tuple, 1);
        var before = LogicalProject.create(tuple, List.of(), List.of(field), List.of("selected"));
        var expected = LogicalProject.create(scan, List.of(), List.of(offset), List.of("selected"));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsDifferentFieldAccess() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scan = builder.scan(table).build();
        var input = builder.getRexBuilder().makeInputRef(scan, 0);
        var offset = builder.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS, input, builder.literal(1));
        var tuple = LogicalProject.create(scan, List.of(), List.of(input, offset), List.of("first", "second"));
        var field = builder.getRexBuilder().makeInputRef(tuple, 0);
        var before = LogicalProject.create(tuple, List.of(), List.of(field), List.of("selected"));
        RuleChecks.doesNotApply(new FoldColumnAccess(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldColumnAccess test...");
        runTest();
    }
}
