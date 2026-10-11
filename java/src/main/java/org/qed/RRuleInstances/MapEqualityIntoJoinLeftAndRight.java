package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — only the INNER-join three-variable case from the rule's own example: the join condition is exactly two cross-side equalities (a.x = b.x AND b.x = a.y) over one shared column type, remapped to (a.x = a.y AND b.x = a.y) to drop one cross-side condition, and the general multi-condition, multi-join-kind equivalence-class remapping driven by MapJoinOpEqualities' functional-dependency closure is not modeled.
public record MapEqualityIntoJoinLeftAndRight() implements RRule {
    // All three columns share one uninterpreted column type, as the real rule
    // only remaps equalities over columns of the same type.
    static final RelType.VarType kT = RexRN.varType("Key_Type", false);

    // Left input: two key-type columns (a.x = 0, a.y = 1); right input: one
    // key-type column (b.x).
    static final RelRN left = RelRN.scanMany("A", Seq.of(kT, kT));
    static final RelRN right = RelRN.scan("B", kT, false);

    // References of the join row (right's column is shifted by the left's two).
    static final RexRN ax = left.joinField(0, right);
    static final RexRN ay = left.joinField(1, right);
    static final RexRN bx = left.joinField(2, right);

    // The two cross-side equality conditions as written, plus the remapped
    // second one re-using the left's second column in place of the right's.
    static final RexRN eqAB = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(ax, bx));
    static final RexRN eqBA = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(bx, ay));
    static final RexRN eqAY = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(ax, ay));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(eqAB, eqBA), right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.and(eqBA, eqAY), right);
    }
}
