package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — only the Eq→Ne pair of NegateComparison's operator map is encoded (NOT(x = y) ⟺ x <> y); the full rule covers 12 operator pairs, each requiring its own before/after pair.
public record NegateComparison() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");
    static final RelRN join = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right);
    static final RexRN x = join.field(0);
    static final RexRN y = join.field(1);

    @Override
    public RelRN before() {
        return join.filter(new RexRN.Not(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, y))));
    }

    @Override
    public RelRN after() {
        return join.filter(new Pred(SqlStdOperatorTable.NOT_EQUALS, Seq.of(x, y)));
    }
}
