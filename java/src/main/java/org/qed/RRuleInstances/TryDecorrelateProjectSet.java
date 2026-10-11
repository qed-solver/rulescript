package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — one column each for left, ProjectSet input, and zip emission; ProjectSet abstracted as an INNER join under uninterpreted membership M and InnerJoinApply as a plain INNER join (correlation/row-emission unmodeled); $private join attributes dropped.
public record TryDecorrelateProjectSet() implements RRule {
    // Left input of the InnerJoinApply (one column).
    static final RelRN L = RelRN.scan("L", "L_Type");

    // The ProjectSet's input (one column).
    static final RelRN I = RelRN.scan("I", "I_Type");

    // The rows emitted by the zip for one input row. ProjectSet(I, zip) is
    // abstracted as an INNER join of I with E under an uninterpreted
    // membership predicate M(I col, E col), proven uniformly for every M.
    static final RelRN E = RelRN.scan("E", "E_Type");

    // Shared uninterpreted symbols, reused in before() and after():
    // M — the zip's row-emission (membership) predicate over (I, E).
    // on — the InnerJoinApply join condition over (L, I, E).
    static final SqlOperator mOp = RuleBuilder.create().genericPredicateOp("M", true);
    static final SqlOperator onOp = RuleBuilder.create().genericPredicateOp("on", true);

    @Override
    public RelRN before() {
        // ProjectSet(I, zip) ≡ Join(I, M, E). M's operands must be absolute
        // columns of the [I, E] join row, via joinField: 0 = I, 1 = E.
        RexRN m = new RexRN.Pred(mOp, Seq.of(I.joinField(0, E), I.joinField(1, E)));
        RelRN ps = I.join(JoinRelType.INNER, m, E);

        // InnerJoinApply(L, ps, on): join row [L, I, E].
        RexRN on = new RexRN.Pred(onOp, Seq.of(
                L.joinField(0, ps), L.joinField(1, ps), L.joinField(2, ps)));
        return L.join(JoinRelType.INNER, on, ps);
    }

    @Override
    public RelRN after() {
        // InnerJoinApply(L, I, []) ≡ cross join; row [L, I].
        RelRN inner = L.join(JoinRelType.INNER, RexRN.trueLiteral(), I);

        // Same zip whose input is now the join: M's operands are absolute
        // columns of the [L, I, E] join row, via joinField: 1 = I, 2 = E.
        RexRN m2 = new RexRN.Pred(mOp, Seq.of(inner.joinField(1, E), inner.joinField(2, E)));
        RelRN ps2 = inner.join(JoinRelType.INNER, m2, E); // row [L, I, E]

        // Select(on, ps2), over the same three columns.
        RexRN on2 = new RexRN.Pred(onOp, Seq.of(
                ps2.field(0), ps2.field(1), ps2.field(2)));
        return ps2.filter(on2);
    }
}
