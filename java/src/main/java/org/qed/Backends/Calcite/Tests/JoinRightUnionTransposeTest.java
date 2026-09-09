package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class JoinRightUnionTransposeTest {
    public static void runTest() {
        var tester = new CalciteTester();
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);

        var left = builder.scan(table.getName()).build();
        var rightA = builder.scan(table.getName()).build();
        var rightB = builder.scan(table.getName()).build();
        var union = builder.push(rightA).push(rightB).union(true, 2).build();

        var before = builder.push(left).push(union)
                .join(JoinRelType.INNER,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var first = builder.push(left).push(rightA)
                .join(JoinRelType.INNER,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var second = builder.push(left).push(rightB)
                .join(JoinRelType.INNER,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var after = builder.push(first).push(second).union(true, 2).build();

        var runner = CalciteTester.loadRule(
                org.qed.Backends.Calcite.Generated.JoinRightUnionTranspose.Config.DEFAULT.toRule());
        tester.verify(runner, before, after);
    }
}
