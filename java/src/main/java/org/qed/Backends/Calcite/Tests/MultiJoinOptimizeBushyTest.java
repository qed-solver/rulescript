package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.MultiJoinOptimizeBushy;
import org.qed.RuleBuilder;

public class MultiJoinOptimizeBushyTest {
    public static void runTest() {
        var rule = new MultiJoinOptimizeBushy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MultiJoinOptimizeBushy rule) {
        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var d = RuleChecks.table(builder, 1, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var left = rel.scan(a).scan(b).join(JoinRelType.INNER, rel.literal(true))
                .scan(c).join(JoinRelType.INNER, rel.literal(true)).build();
        var right = rel.scan(d).build();
        rel.push(left).push(right);
        var rex = rel.getRexBuilder();
        var arguments = new java.util.ArrayList<RexNode>();
        for (var i = 0; i < left.getRowType().getFieldCount(); i++) {
            arguments.add(rex.makeInputRef(left.getRowType().getFieldList().get(i).getType(), i));
        }
        for (var i = 0; i < right.getRowType().getFieldCount(); i++) {
            arguments.add(rex.makeInputRef(right.getRowType().getFieldList().get(i).getType(), left.getRowType().getFieldCount() + i));
        }
        RexNode condition = rel.call(builder.genericPredicateOp("F", true), arguments);
        var before = rel.join(JoinRelType.INNER, condition).build();

        var expected = rel;
        var ab = expected.scan(a).scan(b).join(JoinRelType.INNER, expected.literal(true)).build();
        var cd = expected.scan(c).scan(d).join(JoinRelType.INNER, expected.literal(true)).build();
        expected.push(ab).push(cd);
        var expectedArgs = new java.util.ArrayList<RexNode>();
        for (var i = 0; i < ab.getRowType().getFieldCount(); i++) {
            expectedArgs.add(rex.makeInputRef(ab.getRowType().getFieldList().get(i).getType(), i));
        }
        for (var i = 0; i < cd.getRowType().getFieldCount(); i++) {
            expectedArgs.add(rex.makeInputRef(cd.getRowType().getFieldList().get(i).getType(), ab.getRowType().getFieldCount() + i));
        }
        RexNode expectedCondition = expected.call(builder.genericPredicateOp("F", true),
                expectedArgs);
        var after = expected.join(JoinRelType.INNER, expectedCondition).build();
        RuleChecks.rewrites(rule, before, after);

        var nearMissBuilder = RuleBuilder.create();
        var na = RuleChecks.table(nearMissBuilder, 1, false);
        var nb = RuleChecks.table(nearMissBuilder, 1, false);
        var nc = RuleChecks.table(nearMissBuilder, 1, false);
        var nd = RuleChecks.table(nearMissBuilder, 1, false);
        var nr = RuleChecks.ruleBuilder(nearMissBuilder);
        var nested = nr.scan(na).scan(nb).join(JoinRelType.INNER, nr.literal(true))
                .scan(nc).join(JoinRelType.INNER, nr.literal(true)).build();
        var nearRight = nr.scan(nd).build();
        nr.push(nested).push(nearRight);
        SqlOperator otherPredicate = nearMissBuilder.genericPredicateOp("Other", true);
        var nearArgs = new java.util.ArrayList<RexNode>();
        for (var i = 0; i < nested.getRowType().getFieldCount(); i++) {
            nearArgs.add(nr.getRexBuilder().makeInputRef(nested.getRowType().getFieldList().get(i).getType(), i));
        }
        nearArgs.add(nr.getRexBuilder().makeInputRef(nearRight.getRowType().getFieldList().get(0).getType(), nested.getRowType().getFieldCount()));
        nearArgs.add(nr.getRexBuilder().makeInputRef(nearRight.getRowType().getFieldList().get(0).getType(), nested.getRowType().getFieldCount()));
        var otherCondition = nr.call(otherPredicate, nearArgs);
        var nearMiss = nr.join(JoinRelType.LEFT, otherCondition).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        CalciteTester.generateAndLoad(new MultiJoinOptimizeBushy());
        runTest();
    }
}
