package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.FilterCalcMerge;

import java.util.List;

public class FilterCalcMergeTest {
    public static void runTest() {
        var rule = new FilterCalcMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var input = RuleBuilder.create();
        var source = scan(input, "PositiveInput");
        input.scan(source);
        var x = input.field(0);
        var y = input.field(1);
        var bottom = input.genericPredicateOp("BottomCondition", true);
        var e0 = input.genericProjectionOp("FirstProjection", new RelType.VarType("FirstType", true));
        var e1 = input.genericProjectionOp("SecondProjection", new RelType.VarType("SecondType", true));
        var top = input.genericPredicateOp("TopCondition", true);
        var inputRel = input.filter(input.call(bottom, x, y))
                .project(List.of(input.call(e0, x, y), input.call(e1, x, y))).build();
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(inputRel,
                input.getRexBuilder().makeCall(top, List.of(input.getRexBuilder().makeInputRef(inputRel, 0),
                        input.getRexBuilder().makeInputRef(inputRel, 1))));

        var expected = RuleBuilder.create();
        var expectedSource = scan(expected, "PositiveExpected");
        expected.scan(expectedSource);
        var ex = expected.field(0);
        var ey = expected.field(1);
        var expectedBottom = expected.genericPredicateOp("BottomCondition", true);
        var expectedE0 = expected.genericProjectionOp("FirstProjection", new RelType.VarType("FirstType", true));
        var expectedE1 = expected.genericProjectionOp("SecondProjection", new RelType.VarType("SecondType", true));
        var expectedTop = expected.genericPredicateOp("TopCondition", true);
        var expectedTopCond = expected.call(expectedTop, expected.call(expectedE0, ex, ey), expected.call(expectedE1, ex, ey));
        var expectedCond = expected.call(SqlStdOperatorTable.AND, expectedTopCond,
                expected.call(expectedBottom, ex, ey));
        var after = expected.filter(expectedCond)
                .project(List.of(expected.call(expectedE0, ex, ey), expected.call(expectedE1, ex, ey))).build();
        applyAndCompare("positive hand-built filter/calc case", before, after);
    }

    private static void handBuiltNoMatch() {
        var b = RuleBuilder.create();
        var source = scan(b, "NoMatchInput");
        b.scan(source);
        var bottom = b.genericPredicateOp("BottomCondition", true);
        var e0 = b.genericProjectionOp("FirstProjection", new RelType.VarType("FirstType", true));
        var e1 = b.genericProjectionOp("SecondProjection", new RelType.VarType("SecondType", true));
        var top = b.genericPredicateOp("TopCondition", true);
        var sourceFields = new org.apache.calcite.rex.RexNode[] {
                b.getRexBuilder().makeInputRef(b.peek(), 0), b.getRexBuilder().makeInputRef(b.peek(), 1)
        };
        var input = b.filter(b.call(bottom, sourceFields[0], sourceFields[1]))
                .project(List.of(b.call(e0, sourceFields), b.call(e1, sourceFields))).build();
        var outsidePredicate = b.getRexBuilder().makeCall(top,
                b.getRexBuilder().makeInputRef(input, 0), b.getRexBuilder().makeInputRef(input, 1));
        var identity = org.apache.calcite.rel.logical.LogicalProject.create(input, List.of(),
                List.of(b.getRexBuilder().makeInputRef(input, 0), b.getRexBuilder().makeInputRef(input, 1)),
                input.getRowType());
        var nonMatching = org.apache.calcite.rel.logical.LogicalFilter.create(identity, outsidePredicate);
        RuleChecks.doesNotApply(new FilterCalcMerge(), nonMatching);
    }

    private static String scan(RuleBuilder b, String label) {
        kala.collection.Seq<kala.tuple.Tuple2<RelType, Boolean>> schema = kala.collection.Seq.of(
                kala.tuple.Tuple.of((RelType) new RelType.VarType(label + "X", true), false),
                kala.tuple.Tuple.of((RelType) new RelType.VarType(label + "Y", true), false));
        var table = b.createQedTable(schema);
        b.addTable(table);
        return table.getName();
    }

    private static void applyAndCompare(String description, RelNode before, RelNode expected) {
        var planner = CalciteTester.loadRule(RuleChecks.generated("FilterCalcMerge"), 1);
        planner.setRoot(before);
        var actual = planner.findBestExp();
        if (!actual.deepEquals(expected) && !actual.explain().equals(expected.explain())) {
            CalciteTester.fail(description + " produced an unexpected plan\n> Given source RelNode:\n"
                    + before.explain() + "\n> Actual rewritten RelNode:\n" + actual.explain()
                    + "\n> Expected rewritten RelNode:\n" + expected.explain());
        } else {
            System.out.println("succeeded (" + description + ")");
        }
    }

    public static void main(String[] args) {
        System.out.println("Running FilterCalcMerge test...");
        runTest();
    }
}
