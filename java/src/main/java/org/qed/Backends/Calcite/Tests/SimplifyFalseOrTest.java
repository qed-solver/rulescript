package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyFalseOr;
import org.qed.RuleBuilder;

public class SimplifyFalseOrTest {
    public static void runTest() {
        var rule = new SimplifyFalseOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false, true);
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        var input = builder.scan(table).build();
        var b = RuleBuilder.create().transform(CalciteRuntime::configure);
        b.push(input);
        var field = b.field(0);
        var predicate = b.call(SqlStdOperatorTable.IS_NOT_NULL, field);
        RelNode before = LogicalFilter.create(input, b.call(SqlStdOperatorTable.OR,
                b.literal(false), predicate));
        RelNode after = LogicalFilter.create(input, predicate);
        RuleChecks.rewrites(rule, before, after);

        var otherRuleBuilder = RuleBuilder.create();
        var otherTable = RuleChecks.table(otherRuleBuilder, 1, false, true);
        var otherBuilder = otherRuleBuilder.transform(CalciteRuntime::configure);
        otherBuilder.scan(otherTable);
        var otherField = otherBuilder.field(0);
        var different = otherBuilder.call(SqlStdOperatorTable.IS_NULL, otherField);
        RelNode differentPredicate = LogicalFilter.create(otherBuilder.build(), different);
        RuleChecks.doesNotApply(rule, differentPredicate);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyFalseOr test...");
        runTest();
    }
}
