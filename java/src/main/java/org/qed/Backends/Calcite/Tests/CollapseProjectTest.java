package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CollapseProject;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.doesNotApply;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewrites;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesConcretePredicates;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesPattern;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rejectsNearMisses;
import static org.qed.Backends.Calcite.Tests.RuleChecks.ruleBuilder;
import static org.qed.Backends.Calcite.Tests.RuleChecks.table;

public class CollapseProjectTest {
    public static void runTest() {
        var rule = new CollapseProject();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        cases(rule);
    }

    private static void cases(CollapseProject rule) {
        var builder = RuleBuilder.create();
        var name = table(builder, 2, false);
        var b = ruleBuilder(builder);
        var source = b.scan(name).build();
        var e0 = b.call(SqlStdOperatorTable.PLUS,
                b.getRexBuilder().makeInputRef(source, 0), b.getRexBuilder().makeInputRef(source, 1));
        var lower = b.push(source).project(Seq.of(e0, b.getRexBuilder().makeInputRef(source, 0)).asJava()).build();
        var c0 = b.getRexBuilder().makeInputRef(lower, 0);
        var c1 = b.getRexBuilder().makeInputRef(lower, 1);
        var p0 = b.call(SqlStdOperatorTable.MULTIPLY, c0, c1);
        var p1 = b.call(SqlStdOperatorTable.MINUS, c0, c1);
        var before = b.push(lower).project(Seq.of(p0, p1).asJava()).build();
        var x = b.getRexBuilder().makeInputRef(source, 0);
        var y = b.getRexBuilder().makeInputRef(source, 1);
        var expected = b.push(source).project(Seq.of(
                b.call(SqlStdOperatorTable.MULTIPLY, b.call(SqlStdOperatorTable.PLUS, x, y), x),
                b.call(SqlStdOperatorTable.MINUS, b.call(SqlStdOperatorTable.PLUS, x, y), x)).asJava()).build();
        rewrites(rule, before, expected);

        var leftName = table(builder, 1, false);
        var rightName = table(builder, 1, false);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var join = b.push(left).push(right).join(JoinRelType.LEFT,
                b.call(SqlStdOperatorTable.LESS_THAN, b.getRexBuilder().makeInputRef(left, 0),
                        b.getRexBuilder().makeInputRef(right, 0))).build();
        var leftExpr = b.call(SqlStdOperatorTable.PLUS, b.getRexBuilder().makeInputRef(join, 0),
                b.getRexBuilder().makeInputRef(join, 1));
        var leftLower = b.push(join).project(Seq.of(leftExpr, b.getRexBuilder().makeInputRef(join, 1)).asJava()).build();
        var j0 = b.getRexBuilder().makeInputRef(leftLower, 0);
        var j1 = b.getRexBuilder().makeInputRef(leftLower, 1);
        var nearMiss = b.push(leftLower).project(Seq.of(
                b.call(SqlStdOperatorTable.MULTIPLY, j0, j1),
                b.call(SqlStdOperatorTable.MINUS, j0, j1)).asJava()).build();
        doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CollapseProject test...");
        runTest();
    }
}
