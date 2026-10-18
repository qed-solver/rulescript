package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryRemapJoinOuterColsLeft(t *testing.T) {
	rule := loadRule(t, "TryRemapJoinOuterColsLeft")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	join := e.f.ConstructInnerJoinApply(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(7))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(rc[0]), e.num(7))))
	e.expectRewrite(rule, before, want)

	l2, lc2 := e.scan("t", 1)
	r2, rc2 := e.scan("u", 1)
	nearMissJoin := e.f.ConstructInnerJoin(l2, r2, e.filters(e.f.ConstructEq(e.v(lc2[0]), e.v(rc2[0]))), &memo.JoinPrivate{})
	nearMiss := e.f.ConstructSelect(nearMissJoin, e.filters(e.f.ConstructEq(e.v(lc2[0]), e.num(7))))
	e.expectNoMatch(rule, nearMiss)
}
