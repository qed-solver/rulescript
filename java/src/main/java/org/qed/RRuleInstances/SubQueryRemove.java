package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;

// ORIGIN: Calcite
// SCOPE: PARTIAL — uncorrelated EXISTS subquery directly under a Filter, Calcite's positive logic=TRUE branch (no NOT EXISTS/IN/SCALAR/SOME/UNIQUE or correlated rewrite), single-column outer input
public record SubQueryRemove() implements RRule {
    static final RelRN r = RelRN.scan("R", "R_Type");
    static final RelRN s = RelRN.scan("S", "S_Type");

    record Exists(RelRN rel) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(rel.semantics());
        }
    }

    @Override
    public RelRN before() {
        return r.filter(new Exists(s));
    }

    @Override
    public RelRN after() {
        RelRN probe = s.project(RexRN.trueLiteral());
        RelRN distinct = new RelRN.Aggregate(probe, Seq.of(probe.field(0)), Seq.empty());
        RelRN joined = r.join(JoinRelType.INNER, RexRN.trueLiteral(), distinct);
        return joined.project(joined.field(0));
    }
}
