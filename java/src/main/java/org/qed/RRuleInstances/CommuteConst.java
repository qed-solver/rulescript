package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — only the Eq variant is covered, as a top-level EQUALS filter over a two-column row (inner cross join of two single-column scans of one shared type), proving the universal commutativity law x = y <-> y = x under three-valued semantics; the Plus/Mult/Bit* variants would be uninterpreted projections whose commutativity QED fundamentally cannot know
public record CommuteConst() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");

    // Identical join on both sides, so only the scalar filter is compared.
    static final RelRN join = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right);
    static final RexRN x = join.field(0);
    static final RexRN y = join.field(1);

    @Override
    public RelRN before() {
        // Filter(x = y, Join(L, R, true))
        return join.filter(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, y)));
    }

    @Override
    public RelRN after() {
        // Filter(y = x, Join(L, R, true))
        return join.filter(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(y, x)));
    }
}
