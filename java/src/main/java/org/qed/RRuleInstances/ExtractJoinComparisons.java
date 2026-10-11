// SCOPE: PARTIAL — inner join only, one input column per side, exactly one comparison between a projection over the left column and a projection over the right column
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;
import org.qed.RexRN.Proj;
import org.qed.RuleBuilder;

// Port of CockroachDB's norm rule ExtractJoinComparisons (join.opt):
//
//   L  JOIN  R  ON  f(L.col) cmp g(R.col)
//         =>
//   (L  [col, f(col)])  JOIN  (R  [col, g(col)])  ON  l.f cmp r.g
//   with a final projection dropping the synthesized columns, restoring
//   the original [L.col, R.col] output.
//
// Each comparison operand is an uninterpreted projection (f, g) of the
// single input column of its side, and the comparison itself is an
// uninterpreted predicate (cmp), so the proof is purely structural: both
// sides denote the bag of (x, y) pairs satisfying L(x) ∧ R(y) ∧
// cmp(f(x), g(y)).
public record ExtractJoinComparisons() implements RRule {
    static final RelRN left  = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // Uninterpreted projection operators for the two comparison operands
    // (each applied to the single column of its input side).
    static final SqlOperator fOp =
            RuleBuilder.create().genericProjectionOp("f", RexRN.varType("f_Type", true));
    static final SqlOperator gOp =
            RuleBuilder.create().genericProjectionOp("g", RexRN.varType("g_Type", true));

    // Uninterpreted comparison operator (the rule covers Eq/Lt/Le/Gt/Ge,
    // which are all uninterpreted symbols here).
    static final SqlOperator cmpOp =
            RuleBuilder.create().genericPredicateOp("cmp", true);

    // Template join over the *original* 1-column inputs, only used to build
    // field references into the join's output layout:
    //   field 0 = L.col,  field 1 = R.col
    static final RelRN joinRow = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right);

    @Override
    public RelRN before() {
        RexRN cond = new Pred(cmpOp, Seq.of(
                new Proj(fOp, Seq.of(joinRow.field(0))), // f(L.col)
                new Proj(gOp, Seq.of(joinRow.field(1))))); // g(R.col)
        return left.join(JoinRelType.INNER, cond, right);
    }

    @Override
    public RelRN after() {
        // Push each operand down as a new column on its side:
        //   lp = L  [col, f(col)]   (field 0 = L.col, field 1 = f(L.col))
        //   rp = R  [col, g(col)]   (field 2 = R.col, field 3 = g(R.col))
        RelRN lp = left.project(Seq.of(
                left.field(0),
                new Proj(fOp, Seq.of(left.field(0)))));
        RelRN rp = right.project(Seq.of(
                right.field(0),
                new Proj(gOp, Seq.of(right.field(0)))));

        // Template join over the widened inputs, to name the layout:
        //   field 0 = L.col, field 1 = f(L.col), field 2 = R.col, field 3 = g(R.col)
        RelRN jRow = lp.join(JoinRelType.INNER, RexRN.trueLiteral(), rp);

        RexRN cond = new Pred(cmpOp, Seq.of(
                jRow.field(1), // f(L.col)
                jRow.field(3))); // g(R.col)
        RelRN j = lp.join(JoinRelType.INNER, cond, rp);

        // Drop the synthesized columns, restoring the original output [L.col, R.col].
        return j.project(Seq.of(jRow.field(0), jRow.field(2)));
    }
}