package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the `IsNull(c) -> false` branch is encoded, applied where `c` is statically non-nullable; Spark's NullPropagation rule also folds IsNotNull(non-nullable) -> true, Coalesce null-stripping, In/InSubquery folds, Count folds, and EqualNullSafe, which rely on operator semantics (coalesce, IN, aggregates, Spark-specific null-safe equality) that QED does not model
public record NullPropagation() implements RRule {
    // a statically non-nullable column: the "c" of IsNull(c)
    static final RelType.VarType V = new RelType.VarType("V", false);
    static final RelRN source = RelRN.scan("Source", V, false);
    static final RexRN x = source.field(0);

    @Override
    public RelRN before() {
        // IsNull(c) where c is known non-nullable
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(x)));
    }

    @Override
    public RelRN after() {
        // folded to the constant false
        return source.filter(RexRN.falseLiteral());
    }
}
