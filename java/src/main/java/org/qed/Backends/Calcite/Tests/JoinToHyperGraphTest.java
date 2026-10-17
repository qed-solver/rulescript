package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RelType;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.JoinToHyperGraph;

public class JoinToHyperGraphTest {
    public static void runTest() {
        var rule = new JoinToHyperGraph();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToHyperGraph rule) {
        var beforeBuilder = builder();
        var before = matchingPlan(beforeBuilder, JoinRelType.INNER);
        var afterBuilder = builder();
        var after = rewrittenPlan(afterBuilder);
        RuleChecks.rewrites(rule, before, after);

        var nearMissBuilder = builder();
        var nearMiss = matchingPlan(nearMissBuilder, JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RuleBuilder builder() {
        var builder = RuleBuilder.create();
        for (var i = 0; i < 3; i++) {
            builder.addTable(builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("INTEGER", true), false))));
        }
        return builder;
    }

    private static org.apache.calcite.rel.RelNode matchingPlan(RuleBuilder b, JoinRelType outerType) {
        var a = b.scan("Table_0").build();
        var rightB = b.scan("Table_1").build();
        b.push(a).push(rightB).join(JoinRelType.INNER,
                predicate(b, "pAB", field(b, 2, 0), field(b, 2, 1)));
        var ab = b.build();
        var third = b.scan("Table_2").build();
        return b.push(ab).push(third).join(outerType, b.and(
                predicate(b, "pAC", field(b, 2, 0), field(b, 2, 2)),
                predicate(b, "pBC", field(b, 2, 1), field(b, 2, 2)))).build();
    }

    private static org.apache.calcite.rel.RelNode rewrittenPlan(RuleBuilder b) {
        var a = b.scan("Table_0").build();
        var rightB = b.scan("Table_1").build();
        var rightC = b.scan("Table_2").build();
        var bc = b.push(rightB).push(rightC).join(JoinRelType.INNER,
                predicate(b, "pBC", field(b, 2, 0), field(b, 2, 1))).build();
        return b.push(a).push(bc).join(JoinRelType.INNER, b.and(
                predicate(b, "pAB", field(b, 2, 0), field(b, 2, 1)),
                predicate(b, "pAC", field(b, 2, 0), field(b, 2, 2)))).build();
    }

    private static RexNode field(RelBuilder b, int inputs, int ordinal) {
        return org.qed.Backends.Calcite.CalciteRuntime.field(b, inputs, ordinal);
    }

    private static RexNode predicate(RelBuilder b, String name, RexNode... args) {
        SqlOperator operator = RuleBuilder.create().genericPredicateOp(name, true);
        return b.call(operator, args);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinToHyperGraph test...");
        runTest();
    }
}
