package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushFilterIntoJoinLeftAndRight(t *testing.T) {
	rule := loadRule(t, "PushFilterIntoJoinLeftAndRight")
	e := newEnv(t)
	l, lc := twoNonNullColumns(e)
	r, rc := twoNonNullColumns(e)
	first := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	second := e.f.ConstructEq(e.v(lc[1]), e.v(rc[1]))
	third := e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))
	before := e.f.ConstructInnerJoin(l, r, e.filters(first, second, third), &memo.JoinPrivate{})
	leftFiltered := e.f.ConstructSelect(l, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(lc[1]))))
	rightFiltered := e.f.ConstructSelect(r, e.filters(e.f.ConstructEq(e.v(rc[0]), e.v(rc[1]))))
	want := e.f.ConstructInnerJoin(leftFiltered, rightFiltered, e.filters(first, second), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(first, second), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(first, third, second), &memo.JoinPrivate{}))
}

func TestSQLPushFilterIntoJoinLeftAndRight(t *testing.T) {
	expectSQLRewrites(t, "PushFilterIntoJoinLeftAndRight", "SELECT * FROM (VALUES (1, 2)) AS l(a,b) JOIN (VALUES (1, 2)) AS r(c,d) ON l.a=r.c AND l.b=r.d AND l.a=r.d")
}
