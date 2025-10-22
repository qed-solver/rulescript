// Extracts join condition as a filter above cartesian join
// Pattern: Join(Inner, cond, L, R) → Filter(cond, Join(Inner, TRUE, L, R))
crate::rule! {
    JoinExtractFilterRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            JoinCond(TL, TR) -> Bool,
        },
        from: crate::join!(left, right, Inner, JoinCond(l, r)),
        to: {
            let cartesian = crate::join!(left, right, Inner, true);
            crate::filter!(cartesian, JoinCond(l, r))
        },
    }
}
