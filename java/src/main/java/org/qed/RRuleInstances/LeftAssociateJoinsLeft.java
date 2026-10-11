package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the outer ON is a fixed representative split into two uninterpreted conjuncts (one bound to insideLeft+outsideRight, one unbound referencing insideRight+outsideRight) over single-column scans, not an arbitrary ExtractBound/Unbound decomposition
public record LeftAssociateJoinsLeft() implements RRule {
    // Base relations (single-column scans), mirroring the rule's example:
    //   xy = insideLeft (A), uv = insideRight (B), ab = outsideRight (C).
    static final RelRN A = RelRN.scan("A", "A_Type"); // xy
    static final RelRN B = RelRN.scan("B", "B_Type"); // uv
    static final RelRN C = RelRN.scan("C", "C_Type"); // ab

    // Shared uninterpreted predicate symbols over the outer ON condition
    // ($outsideOn = a=x AND b=u in the example).
    // - bound:   a=x, references insideLeft (A/xy) and outsideRight (C/ab).
    // - unbound: b=u, references insideRight (B/uv) and outsideRight (C/ab).
    static final SqlOperator boundOp = RuleBuilder.create().genericPredicateOp("bound", true);
    static final SqlOperator unboundOp = RuleBuilder.create().genericPredicateOp("unbound", true);

    // ---- before(): (A ⋈_{True} B) ⋈_{bound ∧ unbound} C ----
    // Inner cross join A ⋈ B (empty ON = ON True); columns (A=0, B=1).
    static final RelRN AB = A.join(JoinRelType.INNER, RexRN.trueLiteral(), B);
    // Outer join over (A=0, B=1) with C (index 2):
    //   bound over A(0), C(2);  unbound over B(1), C(2).
    static final RexRN boundBefore = new RexRN.Pred(boundOp, Seq.of(AB.joinField(0, C), AB.joinField(2, C)));
    static final RexRN unboundBefore = new RexRN.Pred(unboundOp, Seq.of(AB.joinField(1, C), AB.joinField(2, C)));

    // ---- after(): B ⋈_{unbound} (A ⋈_{bound} C) ----
    // Inner join A ⋈_{bound} C over (A=0, C=1).
    static final RexRN boundAfter = new RexRN.Pred(boundOp, Seq.of(A.joinField(0, C), A.joinField(1, C)));
    static final RelRN AC = A.join(JoinRelType.INNER, boundAfter, C);
    // Outer join B ⋈_{unbound} (A⋈C) over (B=0, A=1, C=2); unbound over B(0), C(2).
    static final RexRN unboundAfter = new RexRN.Pred(unboundOp, Seq.of(B.joinField(0, AC), B.joinField(2, AC)));
    static final RelRN afterRaw = B.join(JoinRelType.INNER, unboundAfter, AC);

    @Override
    public RelRN before() {
        // (xy ⋈_{True} uv) ⋈_{a=x ∧ b=u} ab; columns (A, B, C).
        return AB.join(JoinRelType.INNER, RexRN.and(boundBefore, unboundBefore), C);
    }

    @Override
    public RelRN after() {
        // uv ⋈_{b=u} (xy ⋈_{a=x} ab), reprojected from (B, A, C) to (A, B, C)
        // so both sides expose the same column order.
        return afterRaw.project(Seq.of(afterRaw.field(1), afterRaw.field(0), afterRaw.field(2)));
    }
}
