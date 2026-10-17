package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PushSelectIntoProjectSet;
import org.qed.RuleBuilder;

public class PushSelectIntoProjectSetTest {
    public static void runTest() {
        var rule = new PushSelectIntoProjectSet();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoProjectSet rule) {
        var builder = RuleBuilder.create();
        var l = table(builder, false);
        var e = table(builder, false);
        SqlOperator bound = builder.genericPredicateOp("bound", false);
        SqlOperator unbound = builder.genericPredicateOp("unbound", false);
        SqlOperator membership = builder.genericPredicateOp("membership", false);
        var inputBuilder = RuleBuilder.create();
        inputBuilder.addTable(l);
        inputBuilder.addTable(e);
        var left = inputBuilder.scan(l.getName()).build();
        var right = inputBuilder.scan(e.getName()).build();
        var condition = inputBuilder.getRexBuilder().makeCall(membership,
                inputBuilder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                inputBuilder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var input = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                condition, java.util.Set.of(), JoinRelType.INNER);
        var boundPredicate = inputBuilder.getRexBuilder().makeCall(bound,
                inputBuilder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0));
        var unboundPredicate = inputBuilder.getRexBuilder().makeCall(unbound,
                inputBuilder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(1).getType(), 1));
        var plan = org.apache.calcite.rel.logical.LogicalFilter.create(input,
                inputBuilder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                        boundPredicate, unboundPredicate));

        RelBuilder plain = RuleChecks.ruleBuilder(builder);
        plain.scan(l.getName()).filter(plain.call(bound, plain.field(0)));
        var expectedSource = plain.build();
        var expectedEmitted = plain.scan(e.getName()).build();
        plain.push(expectedSource).push(expectedEmitted).join(JoinRelType.INNER,
                plain.call(membership, plain.field(2, 0, 0), plain.field(2, 1, 0)));
        var expectedJoin = plain.build();
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(expectedJoin,
                plain.getRexBuilder().makeCall(unbound,
                        plain.getRexBuilder().makeInputRef(expectedJoin.getRowType().getFieldList().get(1).getType(), 1)));
        RuleChecks.rewrites(rule, plan, expected);

        var leftBuilder = RuleBuilder.create();
        leftBuilder.addTable(l);
        leftBuilder.addTable(e);
        var leftInput = leftBuilder.scan(l.getName()).build();
        var rightInput = leftBuilder.scan(e.getName()).build();
        var leftCondition = leftBuilder.getRexBuilder().makeCall(membership,
                leftBuilder.getRexBuilder().makeInputRef(leftInput.getRowType().getFieldList().get(0).getType(), 0),
                leftBuilder.getRexBuilder().makeInputRef(rightInput.getRowType().getFieldList().get(0).getType(), 1));
        var leftJoin = org.apache.calcite.rel.logical.LogicalJoin.create(leftInput, rightInput, java.util.List.of(),
                leftCondition, java.util.Set.of(), JoinRelType.LEFT);
        var leftBound = leftBuilder.getRexBuilder().makeCall(bound,
                leftBuilder.getRexBuilder().makeInputRef(leftJoin.getRowType().getFieldList().get(0).getType(), 0));
        var leftUnbound = leftBuilder.getRexBuilder().makeCall(unbound,
                leftBuilder.getRexBuilder().makeInputRef(leftJoin.getRowType().getFieldList().get(1).getType(), 1));
        var leftPlan = org.apache.calcite.rel.logical.LogicalFilter.create(leftJoin,
                leftBuilder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, leftBound, leftUnbound));
        RuleChecks.doesNotApply(rule, leftPlan);
    }

    private static org.qed.QedTable table(RuleBuilder builder, boolean nullable) {
        var schema = kala.collection.Seq.of(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", nullable), false));
        var table = builder.createQedTable(schema);
        builder.addTable(table);
        return table;
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoProjectSet test...");
        runTest();
    }
}
