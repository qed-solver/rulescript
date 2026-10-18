package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestJoinPushExpressions(t *testing.T) {
	rule := loadRule(t, "JoinPushExpressions")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	leftExpr := e.f.ConstructPlus(e.v(lc[0]), e.num(1))
	rightExpr := e.f.ConstructPlus(e.v(rc[0]), e.num(2))
	beforeCond := e.filters(
		e.f.ConstructEq(leftExpr, rightExpr),
		e.f.ConstructGt(e.v(lc[0]), e.v(rc[0])),
	)
	before := e.f.ConstructInnerJoin(l, r, beforeCond, &memo.JoinPrivate{})

	leftComputed := e.f.Metadata().AddColumn("leftComputed", types.Int)
	rightComputed := e.f.Metadata().AddColumn("rightComputed", types.Int)
	pushedLeft := e.f.ConstructProject(l, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(leftExpr, leftComputed)}, l.Relational().OutputCols)
	pushedRight := e.f.ConstructProject(r, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(rightExpr, rightComputed)}, r.Relational().OutputCols)
	pushedJoin := e.f.ConstructInnerJoin(pushedLeft, pushedRight, e.filters(
		e.f.ConstructEq(e.v(leftComputed), e.v(rightComputed)),
		e.f.ConstructGt(e.v(lc[0]), e.v(rc[0])),
	), &memo.JoinPrivate{})
	want := e.f.ConstructProject(pushedJoin, memo.ProjectionsExpr{}, opt.MakeColSet(lc[0], rc[0]))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(l, r, e.filters(
		e.f.ConstructEq(leftExpr, rightExpr),
		e.f.ConstructGt(e.v(lc[0]), e.num(0)),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
