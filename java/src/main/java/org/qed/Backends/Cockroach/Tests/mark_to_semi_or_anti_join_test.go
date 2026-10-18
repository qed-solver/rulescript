package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMarkToSemiOrAntiJoin(t *testing.T) {
	rule := loadRule(t, "MarkToSemiOrAntiJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	semi := e.f.ConstructSemiJoin(l, r, cond, &memo.JoinPrivate{})
	anti := e.f.ConstructAntiJoin(l, r, cond, &memo.JoinPrivate{})
	semiLeftCol := e.f.Metadata().AddColumn("semi-left", e.f.Metadata().ColumnMeta(lc[0]).Type)
	antiLeftCol := e.f.Metadata().AddColumn("anti-left", e.f.Metadata().ColumnMeta(lc[0]).Type)
	semiMarker := e.f.Metadata().AddColumn("marker", types.Bool)
	antiMarker := e.f.Metadata().AddColumn("marker", types.Bool)
	left := e.f.ConstructProject(semi, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), semiLeftCol), e.f.ConstructProjectionsItem(memo.TrueSingleton, semiMarker),
	}, opt.ColSet{})
	right := e.f.ConstructProject(anti, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(lc[0]), antiLeftCol), e.f.ConstructProjectionsItem(memo.FalseSingleton, antiMarker),
	}, opt.ColSet{})
	marker := e.f.Metadata().AddColumn("union-marker", types.Bool)
	unionCol := e.f.Metadata().AddColumn("union-left", e.f.Metadata().ColumnMeta(lc[0]).Type)
	union := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{semiLeftCol, semiMarker}, RightCols: opt.ColList{antiLeftCol, antiMarker}, OutCols: opt.ColList{unionCol, marker},
	})
	residual := e.f.ConstructNot(e.v(unionCol))
	input := e.f.ConstructSelect(union, e.filters(e.v(marker), residual))
	projectCol := e.f.Metadata().AddColumn("projected", e.f.Metadata().ColumnMeta(lc[0]).Type)
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(unionCol), projectCol)}, opt.ColSet{})

	wantSemi := e.f.ConstructSemiJoin(l, r, cond, &memo.JoinPrivate{})
	wantFilter := e.f.ConstructSelect(wantSemi, e.filters(e.f.ConstructNot(e.v(lc[0]))))
	wantCol := e.f.Metadata().AddColumn("projected", e.f.Metadata().ColumnMeta(lc[0]).Type)
	want := e.f.ConstructProject(wantFilter, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(lc[0]), wantCol)}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearL, nearLC := e.scan("u", 1)
	nearR, nearRC := e.scan("t", 1)
	nearCond := e.filters(e.f.ConstructEq(e.v(nearLC[0]), e.v(nearRC[0])))
	nearSemi := e.f.ConstructSemiJoin(nearL, nearR, nearCond, &memo.JoinPrivate{})
	nearAnti := e.f.ConstructAntiJoin(nearL, nearR, nearCond, &memo.JoinPrivate{})
	nearSemiLeftCol := e.f.Metadata().AddColumn("semi-left", e.f.Metadata().ColumnMeta(nearLC[0]).Type)
	nearAntiLeftCol := e.f.Metadata().AddColumn("anti-left", e.f.Metadata().ColumnMeta(nearLC[0]).Type)
	nearSemiMarker := e.f.Metadata().AddColumn("marker", types.Bool)
	nearAntiMarker := e.f.Metadata().AddColumn("marker", types.Bool)
	nearLeft := e.f.ConstructProject(nearSemi, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(nearLC[0]), nearSemiLeftCol), e.f.ConstructProjectionsItem(memo.TrueSingleton, nearSemiMarker),
	}, opt.ColSet{})
	nearRight := e.f.ConstructProject(nearAnti, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(nearLC[0]), nearAntiLeftCol), e.f.ConstructProjectionsItem(memo.FalseSingleton, nearAntiMarker),
	}, opt.ColSet{})
	nearMarker := e.f.Metadata().AddColumn("union-marker", types.Bool)
	nearUnionCol := e.f.Metadata().AddColumn("union-left", e.f.Metadata().ColumnMeta(nearLC[0]).Type)
	nearUnion := e.f.ConstructUnionAll(nearLeft, nearRight, &memo.SetPrivate{
		LeftCols: opt.ColList{nearSemiLeftCol, nearSemiMarker}, RightCols: opt.ColList{nearAntiLeftCol, nearAntiMarker}, OutCols: opt.ColList{nearUnionCol, nearMarker},
	})
	nearInput := e.f.ConstructSelect(nearUnion, e.filters(e.f.ConstructNot(e.v(nearUnionCol))))
	nearProjectCol := e.f.Metadata().AddColumn("projected", e.f.Metadata().ColumnMeta(nearLC[0]).Type)
	nearBefore := e.f.ConstructProject(nearInput, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(nearUnionCol), nearProjectCol)}, opt.ColSet{})
	e.expectNoMatch(rule, nearBefore)
}
