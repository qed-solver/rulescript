package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSortFiltersInJoin(t *testing.T) {
	rule := loadRule(t, "SortFiltersInJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	a := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	b := e.f.ConstructGt(e.v(lc[0]), e.num(0))
	before := e.f.ConstructInnerJoin(l, r, e.filters(a, b), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(b, a), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(a), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(a, b), &memo.JoinPrivate{}))
}

func TestSQLSortFiltersInJoin(t *testing.T) {
	expectSQLRewrites(t, "SortFiltersInJoin", "SELECT * FROM t JOIN u ON t.a = u.x AND t.b > 0")
}
