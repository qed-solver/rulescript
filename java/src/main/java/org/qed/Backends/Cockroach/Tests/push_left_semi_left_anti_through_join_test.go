package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushLeftSemiLeftAntiThroughJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushLeftSemiLeftAntiThroughJoin")
	x, xc := e.scan("t", 1)
	y, yc := e.scan("u", 1)
	z, zc := e.scan("nn", 1)
	childCond := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0])))
	semiCond := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(zc[0])))
	child := e.f.ConstructInnerJoin(x, y, childCond, memo.EmptyJoinPrivate)
	before := e.f.ConstructSemiJoin(child, z, semiCond, memo.EmptyJoinPrivate)
	pushedSemi := e.f.ConstructSemiJoin(x, z, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(zc[0]))), memo.EmptyJoinPrivate)
	want := e.f.ConstructInnerJoin(pushedSemi, y, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0]))), memo.EmptyJoinPrivate)
	e.expectRewrite(rule, before, want)

	missSemiCond := e.filters(e.f.ConstructEq(e.v(yc[0]), e.v(zc[0])))
	miss := e.f.ConstructSemiJoin(child, z, missSemiCond, memo.EmptyJoinPrivate)
	e.expectNoMatch(rule, miss)
}
