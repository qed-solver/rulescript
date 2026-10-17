package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneJoinLeftCols;
import org.qed.RuleBuilder;

public class PruneJoinLeftColsTest {
    public static void runTest() {
        var rule = new PruneJoinLeftCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneJoinLeftCols rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);

        var leftInput = b.scan(left).build();
        var rightInput = b.scan(right).build();
        b.push(leftInput).push(rightInput);
        var condition = b.call(SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(leftInput.getRowType().getFieldList().get(0).getType(), 0),
                b.getRexBuilder().makeInputRef(rightInput.getRowType().getFieldList().get(0).getType(), 2));
        RelNode beforeJoin = b.join(JoinRelType.INNER, condition).build();
        var outer = b.push(beforeJoin);
        outer.project(outer.call(SqlStdOperatorTable.PLUS, outer.field(0), outer.field(2)));
        RelNode before = outer.build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 2, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 1, false);
        RelBuilder e = RuleChecks.ruleBuilder(expectedBuilder);
        e.push(e.scan(expectedLeft).build());
        e.project(e.field(0));
        e.push(e.scan(expectedRight).build());
        var expectedCondition = e.call(SqlStdOperatorTable.EQUALS,
                e.getRexBuilder().makeInputRef(e.peek(1).getRowType().getFieldList().get(0).getType(), 0),
                e.getRexBuilder().makeInputRef(e.peek(0).getRowType().getFieldList().get(0).getType(), 1));
        e.join(JoinRelType.INNER, expectedCondition);
        e.project(e.call(SqlStdOperatorTable.PLUS, e.field(0), e.field(1)));
        RelNode after = e.build();
        RuleChecks.rewrites(rule, before, after);

        var missBuilder = RuleBuilder.create();
        var missLeft = RuleChecks.table(missBuilder, 2, false);
        var missRight = RuleChecks.table(missBuilder, 1, false);
        RelBuilder m = RuleChecks.ruleBuilder(missBuilder);
        m.push(m.scan(missLeft).build()).push(m.scan(missRight).build());
        var missCondition = m.call(SqlStdOperatorTable.EQUALS,
                m.getRexBuilder().makeInputRef(m.peek(1).getRowType().getFieldList().get(0).getType(), 0),
                m.getRexBuilder().makeInputRef(m.peek(0).getRowType().getFieldList().get(0).getType(), 2));
        m.join(JoinRelType.LEFT, missCondition);
        m.project(m.field(0));
        RuleChecks.doesNotApply(rule, m.build());
        System.out.println("succeeded (ordinary tables and left-join near miss)");
    }

    public static void main(String[] args) {
        System.out.println("Running PruneJoinLeftCols test...");
        runTest();
    }
}
