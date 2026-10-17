package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyTrueOr;
import org.qed.RuleBuilder;

public class SimplifyTrueOrTest {
    public static void runTest() {
        var rule = new SimplifyTrueOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var rel = builder.transform(CalciteRuntime::configure);
        rel.scan(table);
        RelNode input = rel.build();
        var rexBuilder = rel.getRexBuilder();
        rel.push(input);
        var predicate = RuleChecks.gt(rel, rel.field(0), 4);
        var disjunction = rexBuilder.makeCall(SqlStdOperatorTable.OR, rexBuilder.makeLiteral(true), predicate);
        RelNode actualInput = LogicalFilter.create(input, disjunction);
        RelNode expected = input;
        RuleChecks.rewrites(rule, actualInput, expected);

        RelNode meaningfulNoMatch = LogicalFilter.create(input,
                rexBuilder.makeCall(SqlStdOperatorTable.OR, rexBuilder.makeLiteral(false), predicate));
        RuleChecks.doesNotApply(rule, meaningfulNoMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyTrueOr test...");
        runTest();
    }
}
