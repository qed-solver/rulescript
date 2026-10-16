package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — Covers the core individual-serializer pruning case of Spark's ObjectSerializerPruning (Project over SerializeFromObject): the serializer (3 uninterpreted fields over the single input object column, here fields 0 and 2 of 3) is pruned to exactly the fields the outer projection references; the nested-field schema pruning (pruneSerializer / SchemaPruning, gated on serializerNestedSchemaPruningEnabled) is not encoded.
public record ObjectSerializerPruning() implements RRule {
    // The object-consumer's input: a single-column relation carrying the data items.
    static final RelRN source = RelRN.scan("Source", "Obj_Type");
    static final RexRN obj = source.field(0);

    // SerializeFromObject's serializer expressions: uninterpreted functions of the
    // input object column. Fields 0 and 2 are referenced by the outer project;
    // field 1 is not used at all.
    static final RexRN ser0 = obj.proj("ser0", "S0_Type");
    static final RexRN ser1 = obj.proj("ser1", "S1_Type");
    static final RexRN ser2 = obj.proj("ser2", "S2_Type");

    @Override
    public RelRN before() {
        // Project over SerializeFromObject: the serializer materializes all 3
        // fields, the projection references only fields 0 and 2.
        RelRN ser = source.project(Seq.of(ser0, ser1, ser2));
        RexRN out0 = ser.field(0);
        RexRN out1 = ser.field(2);
        return ser.project(Seq.of(out0, out1));
    }

    @Override
    public RelRN after() {
        // The serializer is pruned to the used fields (original positions 0 and 2,
        // order preserved); the projection references the same serializer fields.
        RelRN prunedSer = source.project(Seq.of(ser0, ser2));
        RexRN out0 = prunedSer.field(0);
        RexRN out1 = prunedSer.field(1);
        return prunedSer.project(Seq.of(out0, out1));
    }
}
