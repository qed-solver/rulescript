package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCleanupDynamicPruningFilters(t *testing.T) {
	rule := loadRule(t, "CleanupDynamicPruningFilters")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	r, rc := e.scan("u", 2)
	dpp := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	extra := e.f.ConstructEq(e.v(lc[1]), e.v(rc[1]))
	before := e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(dpp)), r, e.filters(dpp, extra), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(dpp, extra), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(
		e.f.ConstructSelect(l, e.filters(dpp)), r, e.filters(dpp, extra), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(dpp)), r, e.filters(extra), &memo.JoinPrivate{}))
	other := e.f.ConstructGt(e.v(lc[0]), e.num(2))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(dpp)), r, e.filters(other, extra), &memo.JoinPrivate{}))
}
