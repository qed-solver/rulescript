package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — row-preserving (non-Explode, e.g. JsonTuple) generator branch with exactly 2 outputs, outer Project selects exactly 1 generator output and no child attributes.
public record GenerateOptimization() implements RRule {
    // Base is the Generate's child: this branch is row-preserving, so the generate is
    // modeled as a 1:1 ProjectMany of independent uninterpreted generator outputs.
    static final RelRN base = RelRN.scan("Base", "Base_Type");
    static final RexRN out1 = base.proj("jt1", "Jt_Type");
    static final RexRN out2 = base.proj("jt2", "Jt_Type");

    @Override
    public RelRN before() {
        // Project(_, Generate(JsonTuple(_, _, _), out1, out2)): the project uses only out2.
        RelRN gen = base.project(Seq.of(out1, out2));
        return gen.project(gen.field(1));
    }

    @Override
    public RelRN after() {
        // Generate pruned to keep only the used generator output.
        RelRN gen = base.project(Seq.of(out2));
        return gen.project(gen.field(0));
    }
}
