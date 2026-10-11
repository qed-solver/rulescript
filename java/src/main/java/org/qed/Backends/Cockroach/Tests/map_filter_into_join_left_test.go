package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMapFilterIntoJoinLeft(t *testing.T) {
	rule := loadRule(t, "MapFilterIntoJoinLeft")
	e := newEnv(t)
	l, lc := twoNonNullColumns(e)
	r, rc := e.scan("u", 1)
	equality := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	filter := e.f.ConstructEq(e.v(rc[0]), e.v(lc[1]))
	before := e.f.ConstructInnerJoin(l, r, e.filters(equality, filter), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(equality, e.f.ConstructEq(e.v(lc[0]), e.v(lc[1]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(filter, equality), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(equality, e.f.ConstructEq(e.v(lc[0]), e.v(lc[1]))), &memo.JoinPrivate{}))
}

func TestSQLMapFilterIntoJoinLeft(t *testing.T) {
	expectSQLRewrites(t, "MapFilterIntoJoinLeft", "SELECT * FROM (VALUES (1, 2)) AS a(x,y) JOIN (VALUES (1)) AS b(z) ON a.x=b.z AND b.z=a.y")
}
