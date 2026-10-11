package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

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
