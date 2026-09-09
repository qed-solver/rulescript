package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record PruneEmptyUnionAllRight() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Common_Type");
    static final RelRN emptyType = RelRN.scan("EmptyType", "Common_Type");

    @Override
    public RelRN before() {
        return source.union(true, emptyType.empty());
    }

    @Override
    public RelRN after() {
        return source;
    }
}
