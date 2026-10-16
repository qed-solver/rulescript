package org.qed.RRuleInstances;

import org.apache.calcite.rel.RelNode;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — concretized to a 3-way UNION ALL of fixed 2-column constant
// Values, since tuple contents are literal data with no uninterpreted-symbol
// representation in the DSL; the all=false distinct variant is not modeled
public record UnionToValues() implements RRule {

    /** A concrete constant 2-column Values relation: flat row values, 2 per row. */
    record ConstValues(Object... rows) implements RelRN {
        @Override
        public RelNode semantics() {
            return RuleBuilder.create()
                    .values(new String[]{"c0", "c1"}, rows)
                    .build();
        }
    }

    // Input Values relations; row (3,4) is intentionally present in both v1 and v2,
    // so UNION ALL must keep two copies of it.
    static final RelRN v1 = new ConstValues(1, 2, 3, 4);
    static final RelRN v2 = new ConstValues(3, 4, 5, 6);
    static final RelRN v3 = new ConstValues(7, 8);

    @Override
    public RelRN before() {
        return v1.union(true, v2, v3);
    }

    @Override
    public RelRN after() {
        // The rule replaces the union of values by one Values holding the
        // concatenation of all input tuples, multiplicities preserved.
        return new ConstValues(1, 2, 3, 4, 3, 4, 5, 6, 7, 8);
    }
}