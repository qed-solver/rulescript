package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the case where the filter's condition references solely the single deserialized-object column produced by the same uninterpreted projection used in the project (no serialization round-trip elimination, no object-append cases)
public record EliminateSerialization() implements RRule {
    static final RelRN row = RelRN.scan("Row", "RowType");
    static final RexRN deser = row.proj("deser", "ObjType");
    static final RexRN cond = deser.pred("cond");

    @Override
    public RelRN before() {
        return row.filter(cond).project(deser);
    }

    @Override
    public RelRN after() {
        RelRN projected = row.project(deser);
        return projected.filter(projected.field(0).pred("cond"));
    }
}