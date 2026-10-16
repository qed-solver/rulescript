package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the deterministic-comparison branch's IsNull arm (IsNull(x < y) ⟹ IsNull(x) OR IsNull(y)) is encoded; Spark's rule also covers the IsNotNull arm and the Not/GreaterThan/GreaterThanOrEqual/LessThanOrEqual operator families, and each concrete operator/arm needs its own before()/after() pair since QED cannot derive null propagation for an uninterpreted operator symbol
public record NullDownPropagation() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scanMany("Source", Seq.of(V, V));
    static final RexRN x = source.field(0);
    static final RexRN y = source.field(1);

    @Override
    public RelRN before() {
        // IsNull(x < y): a null check over a deterministic comparison
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.LESS_THAN, Seq.of(x, y))
                .pred(SqlStdOperatorTable.IS_NULL));
    }

    @Override
    public RelRN after() {
        // Or(IsNull(x), IsNull(y)): the null check propagated below the comparison's children
        return source.filter(new RexRN.Or(Seq.of(
                x.pred(SqlStdOperatorTable.IS_NULL),
                y.pred(SqlStdOperatorTable.IS_NULL))));
    }
}
