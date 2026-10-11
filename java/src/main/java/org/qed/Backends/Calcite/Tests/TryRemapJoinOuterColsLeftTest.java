package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryRemapJoinOuterColsLeft;
import org.qed.RuleBuilder;

public class TryRemapJoinOuterColsLeftTest {

    public static void runTest() {
        var rule = new TryRemapJoinOuterColsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryRemapJoinOuterColsLeft rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var join = RuleChecks.correlate(b, b.scan(left).build(), b.scan(right).build(), JoinRelType.INNER,
                args -> b.call(SqlStdOperatorTable.EQUALS, args[0], args[1]));
        var before = b.push(join).filter(RuleChecks.gt(b, b.field(0), 5)).build();
        var after = b.push(join).filter(RuleChecks.gt(b, b.field(1), 5)).build();
        RuleChecks.rewrites(rule, before, after);

        var outerJoin = RuleChecks.correlate(b, b.scan(left).build(), b.scan(right).build(), JoinRelType.LEFT,
                args -> b.call(SqlStdOperatorTable.EQUALS, args[0], args[1]));
        RuleChecks.doesNotApply(rule, b.push(outerJoin).filter(RuleChecks.gt(b, b.field(0), 5)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running TryRemapJoinOuterColsLeft test...");
        runTest();
    }
}
