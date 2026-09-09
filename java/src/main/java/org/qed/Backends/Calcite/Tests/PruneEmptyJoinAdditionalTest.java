package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneEmptyJoinAdditionalTest {
    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var left = builder.scan(table.getName()).build();
        var right = builder.scan(table.getName()).build();
        var emptyRight = builder.push(right).empty().build();

        var semiBefore = builder.push(left).push(emptyRight)
                .join(JoinRelType.SEMI,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        var emptyLeft = builder.push(left).empty().build();
        new CalciteTester().verify(
                CalciteTester.loadRule(
                        org.qed.Backends.Calcite.Generated.PruneEmptySemiJoinRight.Config.DEFAULT.toRule()),
                semiBefore, emptyLeft);

        var antiBefore = builder.push(left).push(emptyRight)
                .join(JoinRelType.ANTI,
                        builder.call(builder.genericPredicateOp("condition", true), builder.joinFields()))
                .build();
        new CalciteTester().verify(
                CalciteTester.loadRule(
                        org.qed.Backends.Calcite.Generated.PruneEmptyAntiJoinRight.Config.DEFAULT.toRule()),
                antiBefore, left);
    }
}
