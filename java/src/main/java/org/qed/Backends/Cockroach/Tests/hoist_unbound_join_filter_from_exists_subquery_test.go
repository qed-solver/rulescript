package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistUnboundJoinFilterFromExistsSubquery(t *testing.T) {
	rule := loadRule(t, "HoistUnboundJoinFilterFromExistsSubquery")
	e := newEnv(t)
	outer, _ := e.scan("t", 1)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("nn", 1)
	unbound := e.f.ConstructGt(e.num(2), e.num(1))
	other := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	joinBefore := e.f.ConstructInnerJoin(l, r, e.filters(unbound, other), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(outer, e.filters(e.exists(joinBefore)))
	joinAfter := e.f.ConstructInnerJoin(l, r, e.filters(other), &memo.JoinPrivate{})
	want := e.f.ConstructSelect(outer, e.filters(e.exists(joinAfter), unbound))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	noUnbound := e.f.ConstructInnerJoin(l, r, e.filters(other), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(noUnbound))))
	leftJoin := e.f.ConstructLeftJoin(l, r, e.filters(unbound, other), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(leftJoin))))
}
