package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.DeduplicateSelectFilters;
import org.qed.RuleBuilder;

public class DeduplicateSelectFiltersTest {

    public static void runTest() {
        var rule = new DeduplicateSelectFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RexNode gt5(RelBuilder b) {
        return RuleChecks.gt(b, b.field(0), 5);
    }

    private static RexNode lt10(RelBuilder b) {
        return b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10));
    }

    private static RexNode gt7(RelBuilder b) {
        return RuleChecks.gt(b, b.field(0), 7);
    }

    private static void ordinaryTables(DeduplicateSelectFilters rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(table)
                .filter(gt5(b))
                .filter(lt10(b))
                .filter(gt5(b))
                .build();
        var after = b.scan(table)
                .filter(gt5(b))
                .filter(lt10(b))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var twoFilters = b.scan(table)
                .filter(gt5(b))
                .filter(lt10(b))
                .build();
        RuleChecks.doesNotApply(rule, twoFilters);

        var differentThird = b.scan(table)
                .filter(gt5(b))
                .filter(lt10(b))
                .filter(gt7(b))
                .build();
        RuleChecks.doesNotApply(rule, differentThird);
    }

    public static void main(String[] args) {
        System.out.println("Running DeduplicateSelectFilters test...");
        runTest();
    }
}
