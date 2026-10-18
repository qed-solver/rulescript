package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushLeakproofJoinIntoPermeableBarrierLeft(t *testing.T) {
	rule := loadRule(t, "PushLeakproofJoinIntoPermeableBarrierLeft")
	e := newEnv(t)
	left, lc := e.scan("t", 2)
	right, rc := e.scan("u", 2)
	barrier := e.f.ConstructProject(left, memo.EmptyProjectionsExpr, left.Relational().OutputCols)
	before := e.f.ConstructInnerJoin(barrier, right,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	join := e.f.ConstructInnerJoin(left, right,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(join, memo.EmptyProjectionsExpr, join.Relational().OutputCols)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	blocked, bc := e.scan("t", 2)
	other, oc := e.scan("u", 2)
	blockedProject := e.f.ConstructProject(blocked, memo.EmptyProjectionsExpr, blocked.Relational().OutputCols)
	filteredRight := e.f.ConstructSelect(other, e.filters(e.f.ConstructEq(e.v(oc[0]), e.num(5))))
	blockedJoin := e.f.ConstructInnerJoin(blockedProject, filteredRight,
		e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	pushedJoin := e.f.ConstructInnerJoin(blocked, filteredRight,
		e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, blockedJoin, e.f.ConstructProject(pushedJoin, memo.EmptyProjectionsExpr, pushedJoin.Relational().OutputCols))

	notBarrier := e.f.ConstructInnerJoin(blocked, filteredRight,
		e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, notBarrier)
}
