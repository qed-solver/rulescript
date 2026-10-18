package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinAddRedundantSemiJoin(t *testing.T) {
	rule := loadRule(t, "JoinAddRedundantSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	semi := e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(semi, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{}))
}
