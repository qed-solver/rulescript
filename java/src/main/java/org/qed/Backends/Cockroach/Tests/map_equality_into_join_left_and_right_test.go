package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMapEqualityIntoJoinLeftAndRight(t *testing.T) {
	rule := loadRule(t, "MapEqualityIntoJoinLeftAndRight")
	e := newEnv(t)
	a, ac := twoNonNullColumns(e)
	b, bc := e.scan("u", 1)
	first := e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))
	second := e.f.ConstructEq(e.v(bc[0]), e.v(ac[1]))
	before := e.f.ConstructInnerJoin(a, b, e.filters(first, second), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(a, b, e.filters(second, e.f.ConstructEq(e.v(ac[0]), e.v(ac[1]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(a, b, e.filters(second, first), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(a, b, e.filters(first, e.f.ConstructNe(e.v(bc[0]), e.v(ac[1]))), &memo.JoinPrivate{}))
}

func TestSQLMapEqualityIntoJoinLeftAndRight(t *testing.T) {
	expectSQLRewrites(t, "MapEqualityIntoJoinLeftAndRight", "SELECT * FROM (VALUES (1, 2)) AS a(x,y) JOIN (VALUES (1)) AS b(z) ON a.x=b.z AND b.z=a.y")
}
