package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// SCOPE: PARTIAL — covers only the UnionAll arm (the ExceptAll arm requires bag-minus semantics that QED does not model), with the zero-row right operand modeled as a structurally empty relation to encode HasZeroRows.
public record EliminateSetLeft() implements RRule {
    // Arbitrary-width inputs: three uninterpreted VarTypes, shared by both
    // operands of the set op (set-op inputs must have identical row types).
    static final RelType.VarType T1 = RexRN.varType("T1_Type", true);
    static final RelType.VarType T2 = RexRN.varType("T2_Type", true);
    static final RelType.VarType T3 = RexRN.varType("T3_Type", true);
    static final Seq<RelType.VarType> V = Seq.of(T1, T2, T3);

    static final RelRN leftRaw = RelRN.scanMany("Left", V);
    static final RelRN rightRaw = RelRN.scanMany("Right", V);

    // The colmap: a non-identity permutation (2,0,1) applied identically to
    // both set-op operands so they share one output row type
    // (T3,T1,T2). The right operand has zero rows, so projecting it changes
    // nothing; applying the same colmap keeps the types aligned.
    static final RelRN leftSel = leftRaw.project(Seq.of(
            leftRaw.field(2), leftRaw.field(0), leftRaw.field(1)));
    static final RelRN rightSel = rightRaw.project(Seq.of(
            rightRaw.field(2), rightRaw.field(0), rightRaw.field(1)));

    @Override
    public RelRN before() {
        // UnionAll($left, $right) where $right has zero rows.
        return leftSel.union(true, rightSel.empty());
    }

    @Override
    public RelRN after() {
        // Project($left, $colMap): the projection is already applied to
        // leftSel, so the result is exactly leftSel (no dedup needed —
        // UnionAll is a bag op, so no Aggregate is introduced).
        return leftSel;
    }
}
