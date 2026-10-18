package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushDownJoinThroughUnion(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushDownJoinThroughUnion")

	x, xc := e.scan("t", 1)
	y, yc := e.scan("u", 1)
	right, rc := e.scan("nn", 2)
	unionCol := e.f.Metadata().AddColumn("union", types.Int)
	union := e.f.ConstructUnionAll(x, y, &memo.SetPrivate{
		LeftCols: opt.ColList{xc[0]}, RightCols: opt.ColList{yc[0]}, OutCols: opt.ColList{unionCol},
	})
	before := e.f.ConstructLeftJoin(union, right,
		e.filters(e.f.ConstructEq(e.v(unionCol), e.v(rc[0]))), &memo.JoinPrivate{})

	leftJoin := e.f.ConstructLeftJoin(x, right,
		e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	rightJoin := e.f.ConstructLeftJoin(y, right,
		e.filters(e.f.ConstructEq(e.v(yc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	rightCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("right-left", types.Int),
		e.f.Metadata().AddColumn("right-a", types.Int),
		e.f.Metadata().AddColumn("right-b", types.Int),
	}
	rightProjected := e.f.ConstructProject(rightJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(yc[0]), rightCols[0]),
		e.f.ConstructProjectionsItem(e.v(rc[0]), rightCols[1]),
		e.f.ConstructProjectionsItem(e.v(rc[1]), rightCols[2]),
	}, opt.ColSet{})
	unionCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("union-left", types.Int),
		e.f.Metadata().AddColumn("union-a", types.Int),
		e.f.Metadata().AddColumn("union-b", types.Int),
	}
	unionResult := e.f.ConstructUnionAll(leftJoin, rightProjected, &memo.SetPrivate{
		LeftCols: opt.ColList{xc[0], rc[0], rc[1]},
		RightCols: opt.ColList{rightCols[0], rightCols[1], rightCols[2]}, OutCols: opt.ColList(unionCols),
	})
	want := e.f.ConstructProject(unionResult, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(unionCols[0]), unionCol),
		e.f.ConstructProjectionsItem(e.v(unionCols[1]), rc[0]),
		e.f.ConstructProjectionsItem(e.v(unionCols[2]), rc[1]),
	}, opt.ColSet{})
	e.expectRewriteUpToColumnNames(rule, before, want)

	wideLeft, wideLeftCols := e.scan("t", 2)
	wideRight, wideRightCols := e.scan("u", 1)
	wideUnionCol := e.f.Metadata().AddColumn("union", types.Int)
	wideUnion := e.f.ConstructUnionAll(wideLeft, wideRight, &memo.SetPrivate{
		LeftCols: opt.ColList{wideLeftCols[0]}, RightCols: opt.ColList{wideRightCols[0]}, OutCols: opt.ColList{wideUnionCol},
	})
	nearMiss := e.f.ConstructLeftJoin(wideUnion, right,
		e.filters(e.f.ConstructEq(e.v(wideUnionCol), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
