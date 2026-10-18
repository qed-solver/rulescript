package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.OptimizeUpdateFields;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class OptimizeUpdateFieldsTest {
    public static void runTest() {
        var rule = new OptimizeUpdateFields();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OptimizeUpdateFields rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = ruleBuilder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(RelType.fromString("A_Type", true), false),
                kala.tuple.Tuple.of(RelType.fromString("B_Type", true), false)));
        ruleBuilder.addTable(table);
        var source = ruleBuilder.scan(table.getName()).build();
        RuleChecks.doesNotApply(rule, source);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
