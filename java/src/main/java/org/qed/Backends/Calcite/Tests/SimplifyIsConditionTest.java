package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyIsCondition;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class SimplifyIsConditionTest {

    public static void runTest() {
        var rule = new SimplifyIsCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notNullLeft(rule);
    }

    private static void notNullLeft(SimplifyIsCondition rule) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", false), false),
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var five = builder.literal(5);
        var before = builder.scan(table.getName())
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, builder.field(0), builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table.getName())
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nullableLeft = RuleChecks.table(builder, 2, false);
        RuleChecks.doesNotApply(rule, builder.scan(nullableLeft)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, builder.field(0), builder.field(1)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIsCondition test...");
        runTest();
    }
}
