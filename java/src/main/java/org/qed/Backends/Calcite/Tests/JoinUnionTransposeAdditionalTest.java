package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class JoinUnionTransposeAdditionalTest {
    private static void verifyLeft(JoinRelType type, RelOptRule rule) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var leftA = builder.scan(table.getName()).build();
        var leftB = builder.scan(table.getName()).build();
        var right = builder.scan(table.getName()).build();
        var union = builder.push(leftA).push(leftB).union(true, 2).build();
        var before = join(builder, union, right, type);
        var after = builder.push(join(builder, leftA, right, type))
                .push(join(builder, leftB, right, type)).union(true, 2).build();
        new CalciteTester().verify(CalciteTester.loadRule(rule), before, after);
    }

    private static void verifyRight() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var left = builder.scan(table.getName()).build();
        var rightA = builder.scan(table.getName()).build();
        var rightB = builder.scan(table.getName()).build();
        var union = builder.push(rightA).push(rightB).union(true, 2).build();
        var before = join(builder, left, union, JoinRelType.RIGHT);
        var after = builder.push(join(builder, left, rightA, JoinRelType.RIGHT))
                .push(join(builder, left, rightB, JoinRelType.RIGHT)).union(true, 2).build();
        new CalciteTester().verify(CalciteTester.loadRule(
                CalciteTester.generatedRule("JoinRightUnionTransposeRight")),
                before, after);
    }

    private static RelNode join(RuleBuilder builder, RelNode left, RelNode right,
            JoinRelType type) {
        return builder.push(left).push(right)
                .join(type, builder.call(builder.genericPredicateOp("condition", true),
                        builder.joinFields()))
                .build();
    }

    public static void runTest() {
        verifyLeft(JoinRelType.LEFT,
                CalciteTester.generatedRule("JoinLeftUnionTransposeLeft"));
        verifyLeft(JoinRelType.SEMI,
                CalciteTester.generatedRule("JoinLeftUnionTransposeSemi"));
        verifyLeft(JoinRelType.ANTI,
                CalciteTester.generatedRule("JoinLeftUnionTransposeAnti"));
        verifyRight();
    }
}
