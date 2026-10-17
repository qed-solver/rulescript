package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.qed.RRuleInstances.ProjectFilterTranspose;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public class ProjectFilterTransposeTest {
    public static void runTest() {
        var rule = new ProjectFilterTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectFilterTranspose rule) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.INTEGER, true), false),
                Tuple.of(new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.INTEGER, true), false)));
        builder.addTable(table);
        var source = RelRN.scanMany(table.getName(), Seq.of(
                new RelType.VarType("X_Type", true), new RelType.VarType("Y_Type", true)));
        var x = source.field(0);
        var y = source.field(1);
        var eOp = builder.genericProjectionOp("E", new RelType.VarType("E_Type", true));
        var fOp = builder.genericProjectionOp("F", new RelType.VarType("F_Type", true));
        var pOp = builder.genericPredicateOp("P", true);
        var e = new RexRN.Proj(eOp, Seq.of(x, y));
        var f = new RexRN.Proj(fOp, Seq.of(x));
        var condition = new RexRN.Pred(pOp, Seq.of(e, y));
        var before = source.filter(condition).project(Seq.of(e, f));
        var lowered = source.project(Seq.of(x, y, e, f));
        var afterCondition = new RexRN.Pred(pOp, Seq.of(lowered.field(2), lowered.field(1)));
        var after = lowered.filter(afterCondition).project(Seq.of(lowered.field(2), lowered.field(3)));
        RuleChecks.rewrites(rule, FaithfulPlans.of(before), FaithfulPlans.expected(after));

        var nearMiss = source.filter(new RexRN.Pred(pOp, Seq.of(e, x))).project(Seq.of(e, f));
        RuleChecks.doesNotApply(rule, FaithfulPlans.of(nearMiss));
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
