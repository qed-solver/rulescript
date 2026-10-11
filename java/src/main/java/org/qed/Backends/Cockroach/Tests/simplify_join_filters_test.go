package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyJoinFilters(t *testing.T) {
	rule := loadRule(t, "SimplifyJoinFilters")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	truth := e.f.ConstructTrue()
	before := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructAnd(on, truth)), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(on), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructAnd(truth, on)), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(e.f.ConstructAnd(on, truth)), &memo.JoinPrivate{}))
}

func TestSQLSimplifyJoinFilters(t *testing.T) {
	expectSQLFires(t, "SimplifyJoinFilters", "SELECT * FROM t JOIN u ON t.a = u.x AND TRUE")
}
