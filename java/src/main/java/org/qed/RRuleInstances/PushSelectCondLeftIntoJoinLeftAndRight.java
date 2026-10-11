// SCOPE: PARTIAL — LEFT JOIN only (the rule's documented case); one-column inputs joined on the single equality L.0=R.0; pushed filter is a single unary predicate on the left key column, mapped to the right key column
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;
import org.qed.RuleBuilder;

public record PushSelectCondLeftIntoJoinLeftAndRight() implements RRule {
    static final RelType.VarType keyTy = RexRN.varType("Key_Type", false);
    static final RelRN left = RelRN.scan("L", keyTy, false);
    static final RelRN right = RelRN.scan("R", keyTy, false);

    // ON condition: the single equality L.0 = R.0, which is the equivalence
    // group (GetEquivGroups) that lets a left-bound filter be mapped to the
    // right side (CanMapJoinOpFilter).
    static final RexRN eq = new Pred(
            SqlStdOperatorTable.EQUALS,
            Seq.of(left.joinField(0, right), left.joinField(1, right)));

    // The select filter being pushed: an uninterpreted unary predicate P on
    // the left key column (bound by the left side, IsBoundBy); its mapping
    // into the right side (MapJoinOpFilter) is the same P on the right key
    // column, since the ON equality equates the two columns.
    static final SqlOperator pOp = RuleBuilder.create().genericPredicateOp("P", true);
    static final RexRN condL = new Pred(pOp, Seq.of(left.field(0)));
    static final RexRN condR = new Pred(pOp, Seq.of(right.field(0)));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.LEFT, eq, right)
                .filter(new Pred(pOp, Seq.of(left.joinField(0, right))));
    }

    @Override
    public RelRN after() {
        return left.filter(condL)
                .join(JoinRelType.LEFT, eq, right.filter(condR));
    }
}
