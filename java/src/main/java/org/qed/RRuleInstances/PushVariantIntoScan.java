package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — a single variant column (two requested fields, one referenced by the filter, one only by the projection) plus one non-variant pass-through column, assuming the rewritten struct-typed scan yields the extracted fields materialized from the same variant values (Spark's reader/VariantMetadata guarantee QED cannot model); the full rule recurses over arbitrary plan shapes, any number of variant columns, and arbitrary filter/projection expressions.
public record PushVariantIntoScan() implements RRule {
    // Table: (c: non-variant pass-through column, v: variant column)
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("C_Type", true),
            RexRN.varType("Variant_Type", true)));

    static final RexRN c = source.field(0);          // non-variant column, passes through
    static final RexRN v = source.field(1);          // variant column

    // Uninterpreted variant-extraction functions (deterministic per value):
    // v:a and v:b as in the rule's example
    static final RexRN va = v.proj("ExtractA", "A_Type");
    static final RexRN vb = v.proj("ExtractB", "B_Type");

    // Filter predicate on the extracted field v:a (abstracts e.g. v:a::int = 1)
    static final RexRN filterPred = va.pred("P");

    // The rewritten relation's row: struct<c, a, b, variant> = (c, v:a, v:b, v)
    static final Seq<RexRN> structRow = Seq.of(c, va, vb, v);

    @Override
    public RelRN before() {
        // Project [c, v:a, v:b, v]  over  Filter [P(v:a)]  over  Scan [c, v: variant]
        return source.filter(filterPred).project(structRow);
    }

    @Override
    public RelRN after() {
        // Rewritten scan: relation now produces struct<c, a, b, variant> directly
        RelRN structScan = source.project(structRow);
        // Filter [P(v.0-a)] -> same predicate symbol P applied to the materialized field
        // Project [v.0, v.1, v.2, v.3] -> identity over the struct fields
        return structScan.filter(structScan.field(1).pred("P"))
                .project(Seq.of(
                        structScan.field(0),
                        structScan.field(1),
                        structScan.field(2),
                        structScan.field(3)));
    }
}