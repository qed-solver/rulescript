package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the ProjectSet input has one column, the zip emits one column of rows, there is exactly one bound filter conjunct (on the input column, pushed below the ProjectSet) and one unbound conjunct (on the synthesized column, kept above); the zip's row-emission membership predicate is uninterpreted over the input column and the emitted row, and the bound conjunct does not reference any column the membership predicate depends on (IsBoundBy guard).
public record PushSelectIntoProjectSet() implements RRule {
    // Input of the ProjectSet: one column a (an "input column" of the
    // ProjectSet).
    static final RelRN inner = RelRN.scan("L", "L_Type");

    // E: the rows emitted by the ProjectSet zip for one input row.
    // ProjectSet's row-producing semantics are uninterpretable to QED, so
    // ProjectSet($input, $zip) is abstracted as an INNER join of the input
    // with E under an uninterpreted membership predicate M(input column,
    // emitted row); the identity is then proven uniformly for every M.
    static final RelRN emitted = RelRN.scan("E", "E_Type");

    // Shared uninterpreted symbols, reused in before() and after():
    // M — the zip's row-emission (membership) predicate over (L, E).
    // P — the filter conjunct bound to the input column L (pushed down).
    // Q — the filter conjunct on the synthesized (zip-emitted) column E,
    //     which stays above the ProjectSet.
    static final SqlOperator mOp = RuleBuilder.create().genericPredicateOp("M", true);
    static final SqlOperator pOp = RuleBuilder.create().genericPredicateOp("P", true);
    static final SqlOperator qOp = RuleBuilder.create().genericPredicateOp("Q", true);

    // Join row: 0 = L, 1 = E.
    static final RexRN cond = new RexRN.Pred(mOp, Seq.of(inner.field(0), emitted.field(0)));
    static final RelRN join = inner.join(JoinRelType.INNER, cond, emitted);

    // P bound to the input column: on the join output, column 0 is the
    // input column L itself.
    static final RexRN p = new RexRN.Pred(pOp, Seq.of(join.field(0)));
    static final RexRN q = new RexRN.Pred(qOp, Seq.of(join.field(1)));

    // Before: Select(ProjectSet(L, zip), P AND Q)
    //        ≈ Filter(P(L) AND Q(E), Join(M(L, E), L, E)).
    // After:  the bound conjunct P is pushed onto the ProjectSet input
    // (Select $input $bound) and the unbound conjunct Q stays above:
    //        Filter(Q(E), Join(M(L, E), Filter(P, L), E)).
    static final RexRN pBelow = new RexRN.Pred(pOp, Seq.of(inner.field(0)));
    static final RelRN after = inner.filter(pBelow)
            .join(JoinRelType.INNER, cond, emitted)
            .filter(q);

    @Override
    public RelRN before() {
        return join.filter(RexRN.and(p, q));
    }

    @Override
    public RelRN after() {
        return after;
    }
}
