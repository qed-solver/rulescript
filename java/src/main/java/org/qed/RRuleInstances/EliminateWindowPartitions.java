package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import kala.collection.Seq;

// ORIGIN: Spark
// SCOPE: PARTIAL — QED has no window operator, so the foldable partition expression is modeled as a constant INTEGER literal group key, and the proven fragment is that eliminating a constant group key from a group-by over an uninterpreted aggregate preserves the output bag.
public record EliminateWindowPartitions() implements RRule {

    static final RelRN source = RelRN.scanMany("R",
        Seq.of(new RelType.VarType("key_type", true), new RelType.VarType("payload_type", true)));

    // the foldable (constant) partition expression
    static final RexRN constKey = new RexRN.Const(
        new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.INTEGER, true));

    static final RelType countType = new RelType.VarType("count_type", true);

    @Override
    public RelRN before() {
        // window partition spec: (const, key) — the constant is materialized in a project
        RelRN withConst = source.project(Seq.of(source.field(0), constKey));
        return new RelRN.Aggregate(
            withConst,
            Seq.of(withConst.field(0), withConst.field(1)),
            Seq.of(new RelRN.AggCall("count", false, countType, Seq.of(withConst.field(0)))));
    }

    @Override
    public RelRN after() {
        // window partition spec: (key) only
        RelRN grouped = new RelRN.Aggregate(
            source,
            Seq.of(source.field(0)),
            Seq.of(new RelRN.AggCall("count", false, countType, Seq.of(source.field(0)))));
        return grouped.project(Seq.of(grouped.field(0), constKey, grouped.field(1)));
    }
}