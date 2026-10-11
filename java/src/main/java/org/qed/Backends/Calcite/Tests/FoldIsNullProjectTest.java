package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldIsNullProject;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.ArrayList;

public class FoldIsNullProjectTest {

    public static void runTest() {
        var rule = new FoldIsNullProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldIsNullProject rule) {
        var builder = RuleBuilder.create();
        var notNullFirst = table(builder, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(notNullFirst)
                .project(plain.call(SqlStdOperatorTable.IS_NULL, plain.field(0)), plain.field(1))
                .build();
        var after = plain.scan(notNullFirst)
                .project(plain.literal(false), plain.field(1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nullableFirst = table(builder, true);
        var nearMiss = builder.scan(nullableFirst)
                .project(builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)), builder.field(1))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static String table(RuleBuilder builder, boolean firstNullable) {
        var schema = new ArrayList<kala.tuple.Tuple2<RelType, Boolean>>();
        schema.add(kala.tuple.Tuple.of(RelType.fromString("INTEGER", firstNullable), false));
        schema.add(kala.tuple.Tuple.of(RelType.fromString("INTEGER", true), false));
        var table = builder.createQedTable(kala.collection.Seq.from(schema));
        builder.addTable(table);
        return table.getName();
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIsNullProject test...");
        runTest();
    }
}
