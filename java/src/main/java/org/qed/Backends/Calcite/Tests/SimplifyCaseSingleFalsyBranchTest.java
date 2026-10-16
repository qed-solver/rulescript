package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.SimplifyCaseSingleFalsyBranch;
import org.qed.RuleBuilder;

public class SimplifyCaseSingleFalsyBranchTest {

    public static void runTest() {
        var rule = new SimplifyCaseSingleFalsyBranch();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifyCaseSingleFalsyBranch rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var scan = b.scan(t).build();
        var caseOf = (java.util.function.Function<Boolean, RexNode>) when -> {
            b.push(scan);
            var cond = RuleChecks.gt(b, b.field(0), 0);
            b.build();
            return b.call(SqlStdOperatorTable.OR,
                    b.call(SqlStdOperatorTable.AND, cond, b.literal(when)),
                    b.call(SqlStdOperatorTable.AND, b.call(SqlStdOperatorTable.NOT, cond), nullBool));
        };
        var before = LogicalFilter.create(scan, caseOf.apply(false));
        var after = b.push(scan).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, LogicalFilter.create(scan, caseOf.apply(true)));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyCaseSingleFalsyBranch test...");
        runTest();
    }
}
