package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — only the INNER-join, single-equality, single-column case: ON is exactly (a.x = b.x) AND f(a.x) and the filter conjunct is remapped to f(b.x); the rule's other join kinds, multi-equality equivalence sets, and arbitrary filter expressions are not modeled.
public record MapFilterIntoJoinRight() implements RRule {
    // Both columns share one uninterpreted key type, since the rule only
    // remaps across columns of the same type that are equated in the ON clause.
    static final RelType.VarType kT = RexRN.varType("Key_Type", false);

    // Left input A: column a.x; right input B: column b.x (plain scans, no
    // outer columns, as in the source rule's example instances).
    static final RelRN left = RelRN.scan("A", kT, false);
    static final RelRN right = RelRN.scan("B", kT, false);

    // The uninterpreted filter conjunct being remapped (one argument). In
    // before() it is applied to the left column a.x; in after() to the right
    // side's equivalent column b.x — the same symbol f in both patterns.
    static final SqlOperator fOp = RuleBuilder.create().genericPredicateOp("mapped_conj", true);

    // Join row of A ⋈ B: column 0 = a.x, column 1 = b.x.
    static final RexRN ax = left.joinField(0, right);
    static final RexRN bx = left.joinField(1, right);

    // The equality conjunct in the ON clause establishing a.x ≡ b.x.
    static final RexRN eq = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(ax, bx));

    // The filter conjunct before (left column) and after (right column) the
    // MapJoinOpFilter remap.
    static final RexRN fOnLeft = new RexRN.Pred(fOp, Seq.of(ax));
    static final RexRN fOnRight = new RexRN.Pred(fOp, Seq.of(bx));

    @Override
    public RelRN before() {
        // InnerJoin(A, B, a.x = b.x AND f(a.x))
        return left.join(JoinRelType.INNER, RexRN.and(eq, fOnLeft), right);
    }

    @Override
    public RelRN after() {
        // InnerJoin(A, B, a.x = b.x AND f(b.x)) — the filter conjunct is remapped
        // to use the right side's equivalent column, exactly as
        // MapFilterIntoJoinRight's MapJoinOpFilter does.
        return left.join(JoinRelType.INNER, RexRN.and(eq, fOnRight), right);
    }
}