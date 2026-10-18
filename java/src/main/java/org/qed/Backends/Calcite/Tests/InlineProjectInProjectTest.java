package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.InlineProjectInProject;
import org.qed.RuleBuilder;

import java.util.List;

public final class InlineProjectInProjectTest {
    public static void runTest() {
        var rule = new InlineProjectInProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        handBuiltNoMatch(rule);
    }

    private static void handBuiltMatch(InlineProjectInProject rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var b = RuleChecks.ruleBuilder(ruleBuilder);
        b.scan(table);
        var source = b.peek();
        var input = source.getRowType().getFieldList().get(0).getType();
        var innerExpr = b.call(operator("F"), b.field(0));
        var inner = LogicalProject.create(source, List.of(), List.of(innerExpr), List.of("f"));
        var innerRef = b.getRexBuilder().makeInputRef(inner.getRowType().getFieldList().get(0).getType(), 0);
        var outerExpr = b.call(operator("T"), innerRef);
        var before = LogicalProject.create(inner, List.of(), List.of(outerExpr, innerRef), List.of("t", "f"));

        var expectedInnerExpr = b.call(operator("F"), b.getRexBuilder().makeInputRef(input, 0));
        var expectedOuterExpr = b.call(operator("T"), expectedInnerExpr);
        var expected = LogicalProject.create(source, List.of(), List.of(expectedOuterExpr, expectedInnerExpr),
                List.of("t", "f"));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void handBuiltNoMatch(InlineProjectInProject rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var b = RuleChecks.ruleBuilder(ruleBuilder);
        b.scan(table);
        var source = b.peek();
        var innerExpr = b.call(operator("F"), b.field(0));
        var inner = LogicalProject.create(source, List.of(), List.of(innerExpr), List.of("f"));
        var innerRef = b.getRexBuilder().makeInputRef(inner.getRowType().getFieldList().get(0).getType(), 0);
        var before = LogicalProject.create(inner, List.of(), List.of(innerRef), List.of("f"));
        RuleChecks.doesNotApply(rule, before);
    }

    private static org.apache.calcite.sql.SqlOperator operator(String name) {
        return RuleBuilder.create().genericProjectionOp(name,
                org.qed.RelType.fromString("INTEGER", true));
    }

    public static void main(String[] args) {
        System.out.println("Running InlineProjectInProject test...");
        runTest();
    }
}
