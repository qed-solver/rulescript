package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.GenerateOptimization;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public final class GenerateOptimizationTest {
    public static void runTest() {
        var rule = new GenerateOptimization();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltGeneratorPruning();
        rejectsProjectThatKeepsChildColumn();
    }

    private static void handBuiltGeneratorPruning() {
        var input = input();
        var beforeGenerator = generator(input, "jt1", "jt2");
        var before = LogicalProject.create(beforeGenerator, List.of(),
                List.of(beforeGenerator.getCluster().getRexBuilder().makeInputRef(beforeGenerator, 1)), List.of("out"));

        var expectedGenerator = generator(input, "jt2");
        var expectedRex = expectedGenerator.getCluster().getRexBuilder();
        var expected = LogicalProject.create(expectedGenerator, List.of(), List.of(expectedRex.makeCast(
                new RelType.VarType("Jt_Type", true), expectedRex.makeInputRef(expectedGenerator, 0), true)),
                List.of("out"));
        RuleChecks.rewrites(new GenerateOptimization(), before, expected);
    }

    private static void rejectsProjectThatKeepsChildColumn() {
        var input = input();
        var generator = generator(input, "jt1", "jt2");
        var rex = generator.getCluster().getRexBuilder();
        var plan = LogicalProject.create(generator, List.of(), List.of(
                rex.makeInputRef(generator, 0), rex.makeInputRef(generator, 1)), List.of("out", "child"));
        RuleChecks.doesNotApply(new GenerateOptimization(), plan);
    }

    private static RelNode input() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var relBuilder = builder.transform(CalciteRuntime::configure);
        return relBuilder.scan(table).build();
    }

    private static RelNode generator(RelNode input, String... outputs) {
        var rex = input.getCluster().getRexBuilder();
        var inputRef = rex.makeInputRef(input, 0);
        var expressions = new java.util.ArrayList<RexNode>();
        var names = new java.util.ArrayList<String>();
        for (var output : outputs) {
            var type = new RelType.VarType("Jt_Type", true);
            var function = new RuleBuilder.QedFunction(output, type);
            expressions.add(rex.makeCall(type, function, List.of(inputRef)));
            names.add(output);
        }
        return LogicalProject.create(input, List.of(), expressions, names);
    }

    public static void main(String[] args) {
        System.out.println("Running GenerateOptimization test...");
        runTest();
    }
}
