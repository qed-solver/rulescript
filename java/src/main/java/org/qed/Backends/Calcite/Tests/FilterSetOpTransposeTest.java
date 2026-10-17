package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterSetOpTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterSetOpTransposeTest {
    public static void runTest() {
        var rule = new FilterSetOpTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        positiveConcretePlan();
        rejectsDistinctUnion();
    }

    private static void positiveConcretePlan() {
        var b = builder();
        var left = scan("FILTER_LEFT");
        var right = scan("FILTER_RIGHT");
        var union = b.push(left).push(right).union(true, 2).build();
        var before = b.push(union).filter(b.call(SqlStdOperatorTable.GREATER_THAN,
                b.field(0), b.literal(10))).build();

        var leftFiltered = b.push(left).filter(b.call(SqlStdOperatorTable.GREATER_THAN,
                b.field(0), b.literal(10))).build();
        var rightFiltered = b.push(right).filter(b.call(SqlStdOperatorTable.GREATER_THAN,
                b.field(0), b.literal(10))).build();
        var expected = b.push(leftFiltered).push(rightFiltered).union(true, 2).build();
        RuleChecks.rewrites(new FilterSetOpTranspose(), before, expected);
    }

    private static void rejectsDistinctUnion() {
        var b = builder();
        var left = scan("DISTINCT_LEFT");
        var right = scan("DISTINCT_RIGHT");
        var union = LogicalUnion.create(List.of(left, right), false);
        var condition = b.call(SqlStdOperatorTable.GREATER_THAN,
                b.getRexBuilder().makeInputRef(union, 0), b.literal(10));
        RuleChecks.doesNotApply(new FilterSetOpTranspose(), LogicalFilter.create(union, condition));
    }

    private static RelBuilder builder() {
        return RuleBuilder.create();
    }

    private static RelNode scan(String name) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, false);
        return qed.scan(table).build();
    }

    public static void main(String[] args) {
        System.out.println("Running FilterSetOpTranspose test...");
        runTest();
    }
}
