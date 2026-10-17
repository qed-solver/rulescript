package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ReplaceNullWithFalseInPredicate;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class ReplaceNullWithFalseInPredicateTest {
    public static void runTest() {
        var rule = new ReplaceNullWithFalseInPredicate();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var qed = RuleBuilder.create();
        var table = table(qed, 1, false, true);
        var b = ruleBuilder(qed);
        var scan = b.scan(table).build();
        var ref = b.getRexBuilder().makeInputRef(scan, 0);
        var predicate = gt(b, ref, 7);
        var nullBoolean = b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        RexNode condition = b.getRexBuilder().makeCall(SqlStdOperatorTable.AND, predicate, nullBoolean);
        RelNode input = LogicalFilter.create(scan, condition);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = table(expectedBuilder, 1, false, true);
        var expectedRelBuilder = ruleBuilder(expectedBuilder);
        var expectedScan = expectedRelBuilder.scan(expectedTable).build();
        var expectedRef = expectedRelBuilder.getRexBuilder().makeInputRef(expectedScan, 0);
        RexNode expectedPredicate = gt(expectedRelBuilder, expectedRef, 7);
        RexNode falseBoolean = expectedRelBuilder.getRexBuilder().makeLiteral(false);
        RelNode expected = LogicalFilter.create(expectedScan,
                expectedRelBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                        expectedPredicate, falseBoolean));
        expected = org.apache.calcite.rel.logical.LogicalValues.createEmpty(expected.getCluster(), expected.getRowType());
        rewrites(rule, input, expected);

        var missBuilder = RuleBuilder.create();
        var missTable = table(missBuilder, 1, false, true);
        var missRelBuilder = ruleBuilder(missBuilder);
        var missScan = missRelBuilder.scan(missTable).build();
        var missRef = missRelBuilder.getRexBuilder().makeInputRef(missScan, 0);
        var missPred = gt(missRelBuilder, missRef, 7);
        var nested = missRelBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT,
                missRelBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.AND, missPred, nullBoolean));
        doesNotApply(rule, LogicalFilter.create(missScan, nested));
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceNullWithFalseInPredicate test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("Calcite test failures: " + CalciteTester.failures());
        }
    }
}
