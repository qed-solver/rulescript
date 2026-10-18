package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.MultiJoinProjectTranspose;
import org.qed.RuleBuilder;
import org.qed.RelType;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.List;

public class MultiJoinProjectTransposeTest {
    public static void runTest() {
        var rule = new MultiJoinProjectTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MultiJoinProjectTranspose rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false);
        var rightName = RuleChecks.table(builder, 2, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var projection = builder.genericProjectionOp("T", new RelType.BaseType(SqlTypeName.INTEGER, true));
        var predicate = builder.genericPredicateOp("C", true);
        var projected = b.push(left).project(List.of(b.call(projection, b.field(0), b.field(1)))).build();
        var before = b.push(projected).push(right)
                .join(JoinRelType.INNER, b.call(predicate, b.field(2, 0, 0), b.field(2, 1, 0), b.field(2, 1, 1)))
                .build();

        RelBuilder expectedBuilder = RuleChecks.ruleBuilder(builder);
        var after = expectedBuilder.push(left).push(right)
                .join(JoinRelType.INNER, expectedBuilder.call(predicate,
                        expectedBuilder.call(projection, expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 0, 1)),
                        expectedBuilder.field(2, 1, 0), expectedBuilder.field(2, 1, 1)))
                .project(List.of(expectedBuilder.call(projection, expectedBuilder.field(0), expectedBuilder.field(1)),
                        expectedBuilder.field(2), expectedBuilder.field(3)))
                .build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running MultiJoinProjectTranspose test...");
        runTest();
    }
}
