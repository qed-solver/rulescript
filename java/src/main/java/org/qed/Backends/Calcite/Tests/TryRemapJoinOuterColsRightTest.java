package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryRemapJoinOuterColsRight;
import org.qed.RuleBuilder;

public class TryRemapJoinOuterColsRightTest {

    public static void runTest() {
        var rule = new TryRemapJoinOuterColsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryRemapJoinOuterColsRight rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var h = builder.genericPredicateOp("h", true);
        var before = builder.push(RuleChecks.correlate(builder, builder.scan(l).build(), builder.scan(r).build(),
                        JoinRelType.INNER, args -> builder.call(SqlStdOperatorTable.EQUALS, args[0], args[1])))
                .filter(builder.call(h, builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.push(RuleChecks.correlate(plain, plain.scan(l).build(), plain.scan(r).build(),
                        JoinRelType.INNER, args -> plain.call(SqlStdOperatorTable.EQUALS, args[0], args[1])))
                .filter(plain.call(h, plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = builder.push(RuleChecks.correlate(builder, builder.scan(l).build(), builder.scan(r).build(),
                        JoinRelType.LEFT, args -> builder.call(SqlStdOperatorTable.EQUALS, args[0], args[1])))
                .filter(builder.call(h, builder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running TryRemapJoinOuterColsRight test...");
        runTest();
    }
}
