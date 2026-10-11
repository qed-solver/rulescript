package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullAndOr;
import org.qed.RuleBuilder;

public class FoldNullAndOrTest {

    public static void runTest() {
        var rule = new FoldNullAndOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullConjuncts(rule);
    }

    private static void nullConjuncts(FoldNullAndOr rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var before = b.scan(table)
                .filter(b.call(SqlStdOperatorTable.AND, nullBool, nullBool))
                .build();
        var after = b.scan(table).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        var single = b.scan(table).filter(nullBool).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullAndOr test...");
        runTest();
    }
}
