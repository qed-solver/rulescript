package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneUnionAllCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneUnionAllCols")
	left, lc := e.scan("t", 3)
	right, rc := e.scan("t", 3)
	unionCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("union0", types.Int),
		e.f.Metadata().AddColumn("union1", types.Int),
		e.f.Metadata().AddColumn("union2", types.Int),
	}
	union := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0], lc[1], lc[2]},
		RightCols: opt.ColList{rc[0], rc[1], rc[2]},
		OutCols: opt.ColList{unionCols[0], unionCols[1], unionCols[2]},
	})
	computed := e.f.Metadata().AddColumn("computed", types.Bool)
	before := e.f.ConstructProject(
		union,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(unionCols[0]), e.v(unionCols[2])), computed)},
		opt.ColSet{},
	)

	leftPruned := e.f.ConstructProject(left, nil, opt.MakeColSet(lc[0], lc[2]))
	rightPruned := e.f.ConstructProject(right, nil, opt.MakeColSet(rc[0], rc[2]))
	prunedUnion := e.f.ConstructUnionAll(leftPruned, rightPruned, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0], lc[2]},
		RightCols: opt.ColList{rc[0], rc[2]},
		OutCols: opt.ColList{unionCols[0], unionCols[2]},
	})
	want := e.f.ConstructProject(
		prunedUnion,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(unionCols[0]), e.v(unionCols[2])), computed)},
		opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	distinctUnion := e.f.ConstructUnion(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0], lc[1], lc[2]},
		RightCols: opt.ColList{rc[0], rc[1], rc[2]},
		OutCols: opt.ColList{unionCols[0], unionCols[1], unionCols[2]},
	})
	nearMiss := e.f.ConstructProject(
		distinctUnion,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructEq(e.v(unionCols[0]), e.v(unionCols[2])), computed)},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, nearMiss)
}
