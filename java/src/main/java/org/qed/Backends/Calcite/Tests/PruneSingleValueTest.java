package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelType;
import org.qed.RRuleInstances.PruneSingleValue;
import org.qed.RuleBuilder;

public final class PruneSingleValueTest {
    public static void runTest() {
        var rule = new PruneSingleValue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneSingleValue rule) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("BOOLEAN", false), false)));
        builder.addTable(table);
        var left = builder.scan(table.getName()).build();
        var plan = builder.push(left).values(new String[]{"v"}, true)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.AND,
                        builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.push(left)
                .filter(plain.call(SqlStdOperatorTable.AND, plain.field(0), plain.literal(true)))
                .project(plain.field(0), plain.literal(true)).build();
        RuleChecks.rewrites(rule, plan, expected);
        var nearMiss = builder.push(left).values(new String[]{"v"}, true)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.AND,
                        builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneSingleValue test...");
        runTest();
    }
}
