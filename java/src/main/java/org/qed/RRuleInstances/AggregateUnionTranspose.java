package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the aggregate is a group-by-all with no aggregate calls (dedup / DISTINCT) and the UNION ALL has exactly two inputs sharing the same two-column row type.
public record AggregateUnionTranspose() implements RRule {
    static final Seq<RelType.VarType> rowType = Seq.of(
            RexRN.varType("T0", true),
            RexRN.varType("T1", true)
    );

    static final RelRN a = RelRN.scanMany("A", rowType);
    static final RelRN b = RelRN.scanMany("B", rowType);

    static RelRN dedup(RelRN x) {
        return new RelRN.Aggregate(x, x.fields(), Seq.empty());
    }

    @Override
    public RelRN before() {
        final RelRN union = a.union(true, b);
        return dedup(union);
    }

    @Override
    public RelRN after() {
        final RelRN union = dedup(a).union(true, dedup(b));
        return dedup(union);
    }
}
