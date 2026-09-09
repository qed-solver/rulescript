package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record PruneEmptyUnionAllLeft() implements RRule {
    static final RelRN emptyType = RelRN.scan("EmptyType", "Common_Type");
    static final RelRN source = RelRN.scan("Source", "Common_Type");

    @Override
    public RelRN before() {
        return emptyType.empty().union(true, source);
    }

    @Override
    public RelRN after() {
        return source;
    }
}
