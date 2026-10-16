package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ProjectWindowTranspose;
import org.qed.RuleBuilder;

public class ProjectWindowTransposeTest {

    public static void runTest() {
        var rule = new ProjectWindowTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectWindowTranspose rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var input = b.scan(t).build();
        var needed = b.push(input).project(b.field(0), b.field(1)).build();
        var perPart = b.push(needed).aggregate(b.groupKey(0),
                b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)).as("f"),
                b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1)).as("g")).build();
        var top = (java.util.function.Function<Integer[], RexNode>) c -> b.call(SqlStdOperatorTable.PLUS,
                b.call(SqlStdOperatorTable.PLUS, b.field(c[0]), b.field(c[1])),
                b.call(SqlStdOperatorTable.PLUS, b.field(c[2]), b.field(c[3])));
        var winFull = b.push(input).push(perPart)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = winFull.project(top.apply(new Integer[]{0, 1, 4, 5})).build();
        var winPruned = b.push(needed).push(perPart)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var after = winPruned.project(top.apply(new Integer[]{0, 1, 3, 4})).build();
        RuleChecks.rewrites(rule, before, after);

        var usesPassthrough = b.push(input).push(perPart)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(top.apply(new Integer[]{0, 1, 2, 4})).build();
        RuleChecks.doesNotApply(rule, usesPassthrough);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectWindowTranspose test...");
        runTest();
    }
}
