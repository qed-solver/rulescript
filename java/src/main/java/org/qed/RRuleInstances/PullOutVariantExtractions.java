package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the Aggregate case: one grouping-key column and one non-distinct aggregate f over a single variant extraction ve(v) of a scan column, hoisted into a Project directly below the Aggregate (no Sort/Join cases).
public record PullOutVariantExtractions() implements RRule {
    // Scan with two columns: 0 = grouping key (name), 1 = variant column (v).
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("name_type", true),
            RexRN.varType("variant_type", true)));

    // The hoistable variant extraction, e.g. variant_get(v, '$.p'), modeled as the
    // uninterpreted deterministic projection "ve" applied to the variant column.
    static final RexRN extraction = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("ve", RexRN.varType("ve_type", true)),
            Seq.of(source.field(1)));

    // Before: Aggregate([name], [name, f(ve(v))], child).
    static final RelRN before = new RelRN.Aggregate(
            source,
            Seq.of(source.field(0)),
            Seq.of(extraction.aggCall("f")));

    // After: Aggregate([name], [name, f(_ve0)], Project([name, ve(v) AS _ve0], child)).
    // The raw variant column v is not passed through (consumed solely via the hoisted
    // extraction), exactly as in the source rule.
    static final RelRN extracted = source.project(Seq.of(source.field(0), extraction));
    static final RelRN after = new RelRN.Aggregate(
            extracted,
            Seq.of(extracted.field(0)),
            Seq.of(extracted.field(1).aggCall("f")));

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
