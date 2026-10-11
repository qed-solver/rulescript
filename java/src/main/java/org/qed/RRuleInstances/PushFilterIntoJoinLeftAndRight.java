// SCOPE: PARTIAL — INNER only, two-column inputs, no outer columns, one mappable cross equality plus two supporting cross equalities
package org.qed.RRuleInstances;
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
public record PushFilterIntoJoinLeftAndRight() implements RRule {
    static final RelType.VarType kT = RexRN.varType("Key_Type", false);
    static final RelRN left = RelRN.scanMany("L", Seq.of(kT, kT));
    static final RelRN right = RelRN.scanMany("R", Seq.of(kT, kT));
    static final RexRN e00 = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(left.joinField(0, right), left.joinField(2, right)));
    static final RexRN e11 = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(left.joinField(1, right), left.joinField(3, right)));
    static final RexRN e01 = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(left.joinField(0, right), left.joinField(3, right)));
    static final RexRN fL = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(left.field(0), left.field(1)));
    static final RexRN fR = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(right.field(0), right.field(1)));
    @Override public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(e00, e11, e01), right);
    }
    @Override public RelRN after() {
        return left.filter(fL).join(JoinRelType.INNER, RexRN.and(e00, e11), right.filter(fR));
    }
}
