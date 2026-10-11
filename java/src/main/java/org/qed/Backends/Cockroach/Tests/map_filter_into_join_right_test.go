package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMapFilterIntoJoinRight(t *testing.T) {
	rule := loadRule(t, "MapFilterIntoJoinRight")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	equality := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	filter := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	before := e.f.ConstructInnerJoin(l, r, e.filters(equality, filter), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(equality, e.f.ConstructGt(e.v(rc[0]), e.num(1))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(filter, equality), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(equality, e.f.ConstructGt(e.v(rc[0]), e.num(1))), &memo.JoinPrivate{}))
}

func TestSQLMapFilterIntoJoinRight(t *testing.T) {
	expectSQLRewrites(t, "MapFilterIntoJoinRight", "SELECT * FROM (VALUES (1)) AS a(x) JOIN (VALUES (1)) AS b(y) ON a.x=b.y AND a.x>0")
}
