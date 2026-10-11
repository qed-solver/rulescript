package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: FULL
public record RightAssociateJoinsRight() implements RRule {
    // A = $outsideLeft (ab), B = $insideLeft (xy), C = $insideRight (uv),
    // each modeled as a single-column base relation.
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    // Shared conjuncts of $outsideOn, split exactly as in the source rule:
    // - p_ac: ExtractBoundConditions — IsBoundBy OutputCols2($insideRight, $outsideLeft),
    //   i.e. referencing only A and C (e.g. the example's b = u). Extracted into the
    //   new inner join (A INNER JOIN C).
    // - p_abc: ExtractUnboundConditions — not IsBoundBy, i.e. referencing B (possibly
    //   also A and C) (e.g. the example's a = x). Stays in the outer join with B.
    static final SqlOperator pacOp = RuleBuilder.create().genericPredicateOp("p_ac", true);
    static final SqlOperator pabcOp = RuleBuilder.create().genericPredicateOp("p_abc", true);

    // ---- before: A INNER JOIN (B INNER JOIN C ON true) ON p_ac(A,C) AND p_abc(A,B,C) ----
    // The inner join's ON is [] (true) in the source rule.
    static final RelRN BC = B.join(JoinRelType.INNER, RexRN.trueLiteral(), C);
    // Row context over A INNER JOIN (B INNER JOIN C): 0=A, 1=B, 2=C.
    static final RexRN beforeCond = RexRN.and(
            new RexRN.Pred(pacOp, Seq.of(A.joinField(0, BC), A.joinField(2, BC))),
            new RexRN.Pred(pabcOp, Seq.of(A.joinField(0, BC), A.joinField(1, BC), A.joinField(2, BC))));
    static final RelRN beforeJoin = A.join(JoinRelType.INNER, beforeCond, BC);

    // ---- after: (A INNER JOIN C ON p_ac(A,C)) INNER JOIN B ON p_abc(A,B,C) ----
    // Row context over A INNER JOIN C: 0=A, 1=C — p_ac is unchanged under reassociation.
    static final RelRN AC = A.join(JoinRelType.INNER,
            new RexRN.Pred(pacOp, Seq.of(A.joinField(0, C), A.joinField(1, C))), C);
    // Row context over (A INNER JOIN C) INNER JOIN B: 0=A, 1=C, 2=B — p_abc reordered to match.
    static final RelRN afterJoin = AC.join(JoinRelType.INNER,
            new RexRN.Pred(pabcOp, Seq.of(AC.joinField(0, B), AC.joinField(2, B), AC.joinField(1, B))), B);
    // The source rule's column-ID-based output reorders rows (A,C,B) vs (A,B,C);
    // project back to the same positional output as before so QED compares the
    // same tuple layout.
    static final RelRN afterResult = afterJoin.project(
            Seq.of(afterJoin.field(0), afterJoin.field(2), afterJoin.field(1)));

    @Override
    public RelRN before() {
        // ab INNER JOIN (xy INNER JOIN uv ON true) ON (b=u AND a=x); output row (A,B,C).
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        // (ab INNER JOIN uv ON b=u) INNER JOIN xy ON a=x; rows (A,C,B) reordered to (A,B,C).
        return afterResult;
    }
}
