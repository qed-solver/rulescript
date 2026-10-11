package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NegateOr;
import org.qed.RuleBuilder;

public class NegateOrTest {

    public static void runTest() {
        var rule = new NegateOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(NegateOr rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var seven = builder.literal(7);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.OR,
                                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                                builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table).filter(plain.and(
                        plain.not(RuleChecks.gt(plain, plain.field(0), 5)),
                        plain.not(plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var threeWay = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.OR,
                                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                                builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten),
                                builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), seven))))
                .build();
        RuleChecks.doesNotApply(rule, threeWay);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateOr test...");
        runTest();
    }
}
