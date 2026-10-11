package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RuleBuilder;
import org.qed.RexRN;

// SCOPE: PARTIAL — INNER join only, one single-key equi conjunct, one 2-ary ON-clause item referencing exactly the right key plus one left non-key column, rebound to the left key.
public record MapFilterIntoJoinLeft() implements RRule {
    static final SqlOperator eq = SqlStdOperatorTable.EQUALS;
    static final RelType.VarType keyT = RexRN.varType("Key_Type", false);
    static final RelType.VarType valT = RexRN.varType("Val_Type", false);
    static final RelRN left  = RelRN.scanMany("L", Seq.of(keyT, valT)); // l0=0, y=1
    static final RelRN right = RelRN.scan("R", keyT, false);            // r0=0
    static final SqlOperator g = RuleBuilder.create().genericPredicateOp("filter_item", true);

    static final RexRN l0 = left.joinField(0, right);
    static final RexRN y  = left.joinField(1, right);
    static final RexRN r0 = left.joinField(2, right);

    static final RexRN eqCond   = new RexRN.Pred(eq, Seq.of(l0, r0));
    static final RexRN gOnRight = new RexRN.Pred(g,  Seq.of(r0, y));
    static final RexRN gOnLeft  = new RexRN.Pred(g,  Seq.of(l0, y));

    @Override public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(eqCond, gOnRight), right);
    }
    @Override public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.and(eqCond, gOnLeft), right);
    }
}
