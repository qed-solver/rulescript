package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneProjectCols;
import org.qed.RuleBuilder;

public class PruneProjectColsTest {
    public static void runTest() {
        var rule = new PruneProjectCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneProjectCols rule) {
        var schema = RuleBuilder.create();
        var table = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.ruleBuilder(schema);
        var before = before(b, table);
        var after = after(b, table);
        RuleChecks.rewrites(rule, before, after);

        var nearMissSchema = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissSchema, 1, false);
        var nearMissBuilder = RuleChecks.ruleBuilder(nearMissSchema);
        var nearMiss = nearMiss(nearMissBuilder, nearMissTable);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode before(RelBuilder b, String table) {
        var scan = b.scan(table).build();
        b.push(scan);
        var inner = b.project(b.field(0), b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS,
                b.field(0), b.literal(7)), b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.MULTIPLY,
                b.field(0), b.literal(11))).build();
        b.push(inner);
        return b.project(b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS,
                b.field(0), b.field(1))).build();
    }

    private static RelNode after(RelBuilder b, String table) {
        var scan = b.scan(table).build();
        b.push(scan);
        return b.project(b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS,
                b.field(0), b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS,
                        b.field(0), b.literal(7)))).build();
    }

    private static RelNode nearMiss(RelBuilder b, String table) {
        var scan = b.scan(table).build();
        b.push(scan);
        var inner = b.project(b.field(0), b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS,
                b.field(0), b.literal(7)), b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.MULTIPLY,
                b.field(0), b.literal(11))).build();
        b.push(inner);
        return b.project(b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS,
                b.field(0), b.field(2))).build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("PruneProjectCols test failures: " + CalciteTester.failures());
        }
    }
}
