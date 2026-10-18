package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestReplaceNullWithFalseInPredicate(t *testing.T) {
	rule := loadRule(t, "ReplaceNullWithFalseInPredicate")
	e := newEnv(t)
	scan, cols := e.scan("t", 3)
	p := e.f.ConstructEq(e.v(cols[1]), e.num(1))
	null := e.f.ConstructNull(types.Bool)
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructAnd(p, null)))
	want := e.f.ConstructSelect(scan, e.filters(p, e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(p, null)))
	e.expectNoMatch(rule, nearMiss)
}
