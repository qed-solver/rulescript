package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — all-inner 3-way MultiJoin(A,B,C) with combined condition pAB(A,B) ∧ pAC(A,C), one valid heuristic output: reorder (A⋈B)⋈C to (A⋈C)⋈B, push pAC into the A⋈C join, keep pAB at the root, restore the (A,B,C) column order with a top project; the cost model, outer-join removal, semijoin and self-join handling of the general rule are not modeled
public record LoptOptimizeJoin() implements RRule {
    // Port of Calcite's LoptOptimizeJoinRule (LogicalProject(MultiJoin) /
    // LogicalMultiJoin). The rule decomposes a MultiJoin into a left-deep
    // chain of binary joins, splits the combined condition into conjuncts,
    // pushes each conjunct to the lowest join whose inputs cover it, and
    // adds a top project when the chosen ordering changes the MultiJoin's
    // column order. The ordering is chosen by a runtime cost model, which
    // is backend-internal and not modelable by QED, so the ordering is
    // pinned to one valid all-inner instance: (A,B,C) -> (A,C,B). This lets
    // pAC (referencing only A,C) be pushed into A⋈C and leaves pAB
    // (needing B) at the root.
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    // The MultiJoin's two filters as shared uninterpreted 2-argument
    // predicate symbols: pAB references only (A, B), pAC only (A, C).
    static final SqlOperator pAB = RuleBuilder.create().genericPredicateOp("pAB", true);
    static final SqlOperator pAC = RuleBuilder.create().genericPredicateOp("pAC", true);

    // before: the MultiJoin(A, B, C | pAB ∧ pAC) flattened to a left-deep
    // chain in original factor order. The inner true-condition join stands
    // in for the MultiJoin's pairwise-unconnected factors; the root
    // carries the MultiJoin's single combined condition. ab has layout
    // (A,B), so in the root join ab⋈C (layout A,B,C): joinField(0)=A,
    // joinField(1)=B, joinField(2)=C — pAB on (A,B), pAC on (A,C).
    static final RelRN ab = A.join(JoinRelType.INNER, RexRN.trueLiteral(), B);
    static final RexRN rootCond = RexRN.and(
            new RexRN.Pred(pAB, Seq.of(ab.joinField(0, C), ab.joinField(1, C))),
            new RexRN.Pred(pAC, Seq.of(ab.joinField(0, C), ab.joinField(2, C))));
    static final RelRN beforeJoin = ab.join(JoinRelType.INNER, rootCond, C);

    // after: the heuristic's chosen left-deep ordering (A, C, B) with each
    // filter pushed as far down as its references allow: pAC needs only
    // {A,C}, which the A⋈C join provides, so it is attached there; pAB
    // needs B, present only at the root, so it stays on (A⋈C)⋈B. ac has
    // layout (A,C), so in the root join ac⋈B (layout A,C,B):
    // joinField(0)=A, joinField(1)=C, joinField(2)=B — pAB on (A,B). The
    // top project restores the original (A,B,C) column order
    // (0=A, 2=B, 1=C).
    static final RelRN ac = A.join(JoinRelType.INNER,
            new RexRN.Pred(pAC, Seq.of(A.joinField(0, C), A.joinField(1, C))), C);
    static final RelRN acb = ac.join(JoinRelType.INNER,
            new RexRN.Pred(pAB, Seq.of(ac.joinField(0, B), ac.joinField(2, B))), B);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return acb.project(Seq.of(acb.field(0), acb.field(2), acb.field(1)));
    }
}
