package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSemiJoinFilterTranspose(t *testing.T) {
	rule := loadRule(t, "SemiJoinFilterTranspose")
	e := newEnv(t)
	l, lc := e.scan("t", 2)
	r, rc := e.scan("u", 2)
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	above := func() memo.FiltersExpr { return e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(1))) }
	jp := func() *memo.JoinPrivate { return &memo.JoinPrivate{} }

	before := e.f.ConstructSemiJoin(e.f.ConstructSelect(l, above()), r, on(), jp())
	want := e.f.ConstructSelect(e.f.ConstructSemiJoin(l, r, on(), jp()), above())
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on(), jp()))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, e.f.ConstructSelect(r, e.filters(
		e.f.ConstructGt(e.v(rc[0]), e.num(1)))), on(), jp()))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(e.f.ConstructSelect(l, above()), r, on(), jp()))
	e.expectNoMatch(rule, want)
}
