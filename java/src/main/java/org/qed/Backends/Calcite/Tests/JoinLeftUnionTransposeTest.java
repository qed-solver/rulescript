package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class JoinLeftUnionTransposeTest {
    public static void runTest() {
        var tester = new CalciteTester();
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);

        var leftA = builder.scan(table.getName()).build();
        var leftB = builder.scan(table.getName()).build();
        var right = builder.scan(table.getName()).build();
        var union = builder.push(leftA).push(leftB).union(true, 2).build();

        var before = builder.push(union).push(right)
                .join(JoinRelType.INNER,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var first = builder.push(leftA).push(right)
                .join(JoinRelType.INNER,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var second = builder.push(leftB).push(right)
                .join(JoinRelType.INNER,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var after = builder.push(first).push(second).union(true, 2).build();

        var runner = CalciteTester.loadRule(
                org.qed.Backends.Calcite.Generated.JoinLeftUnionTranspose.Config.DEFAULT.toRule());
        tester.verify(runner, before, after);
    }
}
