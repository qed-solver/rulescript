package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyIsNullCondition;
import org.qed.RuleBuilder;

public class SimplifyIsNullConditionTest {

    public static void runTest() {
        var rule = new SimplifyIsNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notNullColumn(rule);
    }

    private static void notNullColumn(SimplifyIsNullCondition rule) {
        var base = RuleBuilder.create();
        var table = mixedTable(base);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(table)
                .filter(RuleChecks.gt(b, b.field(1), 5))
                .filter(b.call(SqlStdOperatorTable.IS_NULL, b.field(0)))
                .build();
        var after = b.scan(table)
                .filter(b.literal(false))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nullableColumn = base.scan(table)
                .filter(RuleChecks.gt(base, base.field(0), 5))
                .filter(base.call(SqlStdOperatorTable.IS_NULL, base.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, nullableColumn);
    }

    private static String mixedTable(RuleBuilder builder) {
        var schema = new java.util.ArrayList<kala.tuple.Tuple2<org.qed.RelType, Boolean>>();
        schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", false), false));
        schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false));
        var table = builder.createQedTable(kala.collection.Seq.from(schema));
        builder.addTable(table);
        return table.getName();
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIsNullCondition test...");
        runTest();
    }
}
