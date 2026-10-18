package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.collection.Set;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.QedTable;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RRuleInstances.MergeProjects;
import org.qed.RuleBuilder;

public class MergeProjectsTest {
    public static void runTest() {
        System.out.println("Running MergeProjects test...");
        var rule = new MergeProjects();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var sourceType = RelType.fromString("Source_Type", true);
        var bottomType = RelType.fromString("Bottom_Type", true);
        var topType = RelType.fromString("Top_Type", true);
        var source = new QedTable("OrdinarySource", Seq.of("value"), Seq.of(sourceType), Set.empty(), Set.empty());
        builder.addTable(source);
        var top = builder.genericProjectionOp("Top", topType);
        var bottom = builder.genericProjectionOp("Bottom", bottomType);
        builder.scan("OrdinarySource");
        var sourcePlan = builder.build();
        var sourceField = builder.getRexBuilder().makeInputRef(sourcePlan, 0);
        var inner = project(sourcePlan, java.util.List.of(sourceField, builder.call(bottom, sourceField)),
                java.util.List.of("value", "computed"));
        var before = project(inner, java.util.List.of(builder.call(top,
                builder.getRexBuilder().makeInputRef(inner, 0))), java.util.List.of("result"));

        var expectedBuilder = RuleBuilder.create();
        expectedBuilder.addTable(new QedTable("OrdinarySource", Seq.of("value"), Seq.of(sourceType),
                Set.empty(), Set.empty()));
        var expectedTop = expectedBuilder.genericProjectionOp("Top", topType);
        expectedBuilder.scan("OrdinarySource");
        var expectedSource = expectedBuilder.build();
        var after = project(expectedSource, java.util.List.of(expectedBuilder.call(expectedTop,
                expectedBuilder.getRexBuilder().makeInputRef(expectedSource, 0))), java.util.List.of("result"));
        RuleChecks.rewrites(rule, before, after);

        var nearMissBuilder = RuleBuilder.create();
        var wideSource = new QedTable("WideSource", Seq.of("first", "second"),
                Seq.of(sourceType, sourceType), Set.empty(), Set.empty());
        nearMissBuilder.addTable(wideSource);
        var nearTop = nearMissBuilder.genericProjectionOp("Top", topType);
        var nearBottom = nearMissBuilder.genericProjectionOp("Bottom", bottomType);
        nearMissBuilder.scan("WideSource");
        var widePlan = nearMissBuilder.build();
        var firstField = nearMissBuilder.getRexBuilder().makeInputRef(widePlan, 0);
        var nearInner = project(widePlan, java.util.List.of(firstField,
                nearMissBuilder.call(nearBottom, firstField)), java.util.List.of("first", "computed"));
        var nearMiss = project(nearInner, java.util.List.of(nearMissBuilder.call(nearTop,
                nearMissBuilder.getRexBuilder().makeInputRef(nearInner, 0))), java.util.List.of("result"));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static LogicalProject project(RelNode input, java.util.List<org.apache.calcite.rex.RexNode> expressions,
                                          java.util.List<String> names) {
        return LogicalProject.create(input, java.util.List.of(), expressions, names);
    }

    public static void main(String[] args) {
        runTest();
    }
}
