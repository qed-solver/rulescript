package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

public record ProjectJoinRemove() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", false);
    static final RelRN t = RelRN.scan("T", V, true);

    static final RelRN joinRow = t.join(JoinRelType.LEFT, RexRN.trueLiteral(), t);

    static final RexRN cond = new Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(joinRow.field(0), joinRow.field(1)));

    @Override
    public RelRN before() {
        RelRN join = t.join(JoinRelType.LEFT, cond, t);
        return join.project(join.field(0));
    }

    @Override
    public RelRN after() {
        return t.project(t.field(0));
    }
}
