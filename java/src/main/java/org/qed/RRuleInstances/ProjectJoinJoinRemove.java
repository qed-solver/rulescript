package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// ORIGIN: Calcite
// SCOPE: PARTIAL — LEFT/LEFT join chain only, a self-join of the same unique single non-nullable-column scan on equality of that column for the bottom join (as in the proven ProjectJoinRemove precedent — every row matches itself, so the null-extension term never fires and no duplicates are introduced), plus a top join to a second single non-nullable-column scan C on that same column, with the outer project keeping only the preserved left column and C's column (never the eliminated join's right column).
public record ProjectJoinJoinRemove() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", false);
    static final RelRN t = RelRN.scan("T", V, true);
    static final RelRN c = RelRN.scan("C", V, false);

    static final RelRN bottomJoin = t.join(JoinRelType.LEFT,
            new Pred(SqlStdOperatorTable.EQUALS, Seq.of(t.joinField(0, t), t.joinField(1, t))), t);

    static final RelRN topJoin = bottomJoin.join(JoinRelType.LEFT,
            new Pred(SqlStdOperatorTable.EQUALS,
                    Seq.of(new RexRN.JoinField(0, bottomJoin, c), new RexRN.JoinField(2, bottomJoin, c))),
            c);

    static final RelRN directJoin = t.join(JoinRelType.LEFT,
            new Pred(SqlStdOperatorTable.EQUALS, Seq.of(t.joinField(0, c), t.joinField(1, c))), c);

    @Override
    public RelRN before() {
        return topJoin.project(Seq.of(topJoin.field(0), topJoin.field(2)));
    }

    @Override
    public RelRN after() {
        return directJoin.project(Seq.of(directJoin.field(0), directJoin.field(1)));
    }
}
