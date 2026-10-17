package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.MarkToSemiOrAntiJoin;
import org.qed.RuleBuilder;

public class MarkToSemiOrAntiJoinTest {
    public static void runTest() {
        var rule = new MarkToSemiOrAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MarkToSemiOrAntiJoin rule) {
        var builder = RuleBuilder.create();
        var leftTable = builder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        var rightTable = builder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        builder.addTable(leftTable).addTable(rightTable);
        var leftName = leftTable.getName();
        var rightName = rightTable.getName();
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var condition = b.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var semi = b.push(left).push(right).join(JoinRelType.SEMI, condition).build();
        var anti = b.push(left).push(right).join(JoinRelType.ANTI, condition).build();
        var marked = b.push(semi).project(b.getRexBuilder().makeInputRef(semi.getRowType().getFieldList().get(0).getType(), 0), b.literal(true)).build();
        var unmatched = b.push(anti).project(b.getRexBuilder().makeInputRef(anti.getRowType().getFieldList().get(0).getType(), 0), b.literal(false)).build();
        var union = b.push(marked).push(unmatched).union(true).build();
        var before = b.push(union).filter(b.and(
                b.getRexBuilder().makeInputRef(union.getRowType().getFieldList().get(1).getType(), 1),
                RuleChecks.gt(b, b.getRexBuilder().makeInputRef(union.getRowType().getFieldList().get(0).getType(), 0), 0)))
                .project(b.getRexBuilder().makeInputRef(union.getRowType().getFieldList().get(0).getType(), 0)).build();

        var expectedTables = RuleBuilder.create();
        expectedTables.addTable(leftTable).addTable(rightTable);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedTables);
        var l = expectedBuilder.scan(leftName).build();
        var r = expectedBuilder.scan(rightName).build();
        var eq = expectedBuilder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                expectedBuilder.getRexBuilder().makeInputRef(l.getRowType().getFieldList().get(0).getType(), 0),
                expectedBuilder.getRexBuilder().makeInputRef(r.getRowType().getFieldList().get(0).getType(), 1));
        var expected = expectedBuilder.push(l).push(r).join(JoinRelType.SEMI, eq)
                .filter(RuleChecks.gt(expectedBuilder, expectedBuilder.getRexBuilder().makeInputRef(l, 0), 0))
                .project(expectedBuilder.getRexBuilder().makeInputRef(l, 0)).build();
        RuleChecks.rewrites(rule, before, expected);

        var nearBuilder = RuleChecks.ruleBuilder(builder);
        var nearCondition = nearBuilder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                nearBuilder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                nearBuilder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        nearBuilder.push(left).push(right);
        var near = nearBuilder.join(JoinRelType.LEFT, nearCondition).build();
        RuleChecks.doesNotApply(rule, near);
    }

    public static void main(String[] args) {
        System.out.println("Running MarkToSemiOrAntiJoin test...");
        runTest();
    }
}
