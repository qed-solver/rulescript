package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.EliminateSetLeft;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

import kala.collection.Seq;

import java.util.List;

public final class EliminateSetLeftTest {
    public static void runTest() {
        var rule = new EliminateSetLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsNonemptyRightArm();
    }

    private static void rewritesHandBuiltPlan() {
        var types = Seq.of(new RelType.VarType("T1_Type", true), new RelType.VarType("T2_Type", true),
                new RelType.VarType("T3_Type", true));
        var leftRaw = RelRN.scanMany("Left", types);
        var rightRaw = RelRN.scanMany("Right", types);
        var leftProjection = leftRaw.project(Seq.of(leftRaw.field(2), leftRaw.field(0), leftRaw.field(1)));
        var rightProjection = rightRaw.project(Seq.of(rightRaw.field(2), rightRaw.field(0), rightRaw.field(1)));
        var source = LogicalUnion.create(List.of(leftProjection.semantics(), rightProjection.empty().semantics()), true);
        var expected = leftProjection.semantics();
        RuleChecks.rewrites(rule(), source, expected);
    }

    private static void rejectsNonemptyRightArm() {
        var ruleBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var leftTable = RuleChecks.table(ruleBuilder, 3, false);
        var rightTable = RuleChecks.table(ruleBuilder, 3, false);
        var left = builder.scan(leftTable).project(List.of(builder.field(2), builder.field(0), builder.field(1))).build();
        var right = builder.scan(rightTable).project(List.of(builder.field(2), builder.field(0), builder.field(1))).build();
        var plan = LogicalUnion.create(List.of(left, right), true);
        RuleChecks.doesNotApply(rule(), plan);
    }

    private static EliminateSetLeft rule() {
        return new EliminateSetLeft();
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSetLeft test...");
        runTest();
    }
}
